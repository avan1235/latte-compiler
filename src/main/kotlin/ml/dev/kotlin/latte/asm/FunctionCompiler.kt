package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Cmd.*
import ml.dev.kotlin.latte.asm.Reg.*
import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.NumOp
import ml.dev.kotlin.latte.syntax.PrimitiveType.IntType
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.syntax.UnOp
import ml.dev.kotlin.latte.util.AsmBuildException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.then
import ml.dev.kotlin.latte.util.unit

class FunctionCompiler(
  private val analysis: FlowAnalysis,
  private val strings: Map<String, Label>,
  private val vTables: Map<Type, VirtualTable>,
  private val result: StringBuilder,
  private val labelGenerator: () -> Label,
  strategy: AllocatorStrategyProducer
) {
  private val allocator = Allocator(analysis, strategy)

  internal fun compile() {
    analysis.statements.forEachIndexed { idx, stmt -> stmt.compile(idx) }
  }

  private fun Quadruple.compile(idx: StmtIdx): Unit = when (this@compile) {
    is AssignQ -> assign(to, from.get(), idx)
    is FunCallQ -> {
      preserveReg(ECX, EDX, at = idx) {
        args.asReversed().forEach { cmd(PUSH, it.get()) }
        cmd(CALL, label)
        argsSize.takeIf { it > 0 }?.let { cmd(ADD, ESP, it.imm) }
      }
      assign(to, EAX, idx)
    }
    is MethodCallQ -> {
      preserveReg(ECX, EDX, at = idx) {
        args.asReversed().forEach { cmd(PUSH, it.get()) }
        cmd(PUSH, self.get())
        cmd(CALL, self.vTable()[ident, argsTypes])
        cmd(ADD, ESP, argsSize.imm)
      }
      assign(to, EAX, idx)
    }
    is RelCondJumpQ -> {
      val left = left.get()
      val right = right.get()
      val (l, r, rev) = LR(left, right).match(
        standard = { LR(left, right) },
        bothMem = { cmd(MOV, EAX, left).then { LR(EAX, right) } },
        bothImm = { cmd(MOV, EAX, left).then { LR(EAX, right) } },
        leftImm = { LR(right, left, rev = true) }
      )
      cmd(CMP, l, r)
      if (rev) cmd(op.symmetric.jump, toLabel)
      else cmd(op.jump, toLabel)
    }
    is CondJumpQ -> {
      cmd(MOV, EAX, cond.get())
      cmd(TEST, EAX, EAX)
      cmd(JNZ, toLabel)
    }
    is JumpQ -> cmd(JMP, toLabel)
    is FunCodeLabelQ -> with(allocator) {
      label.insert()
      cmd(PUSH, EBP)
      cmd(MOV, EBP, ESP)
      cmd(SUB, ESP, localsOffset.imm, localsOffset > 0)
      savedByCallee.forEach { cmd(PUSH, it) }
    }
    is RetQ -> with(allocator) {
      value?.let { cmd(MOV, EAX, it.get()) }
      savedByCallee.asReversed().forEach { cmd(POP, it) }
      cmd(MOV, ESP, EBP, localsOffset > 0)
      cmd(POP, EBP)
      cmd(RET)
    }
    is CodeLabelQ -> label.insert()
    is UnOpQ -> op.on(to, from, idx)
    is BinOpQ -> op.on(to, left, right, idx)
    is UnOpModQ -> when (to.get()) {
      is Reg -> AssignQ(to, from).compile(idx).then { cmd(op.cmd, to.get()) }
      is Mem -> AssignQ(to, from).compile(idx).then { cmd(op.cmd, to.get()) }
      else -> err("Unexpected case for $this")
    }
    is PhonyQ -> err("Unexpected $this found in compilation phase")
    is LoadQ -> TODO()
    is StoreQ -> TODO()
  }

  private fun NumOp.on(to: VirtualReg, left: ValueHolder, right: ValueHolder, idx: StmtIdx): Unit = when (this) {
    NumOp.PLUS -> matchLR(to, left, right, idx).match(
      standard = { cmd(ADD, l, r) },
      bothMem = {
        cmd(MOV, EAX, r)
        cmd(ADD, l, EAX)
      },
    )
    NumOp.MINUS -> matchLR(to, left, right, idx).match(
      standard = {
        cmd(SUB, l, r)
        if (rev) cmd(NEG, l)
      },
      bothMem = {
        if (!rev) {
          cmd(MOV, EAX, r)
          cmd(SUB, l, EAX)
        } else {
          cmd(MOV, EAX, r)
          cmd(SUB, EAX, l)
          cmd(MOV, r, EAX)
        }
      },
    )
    NumOp.TIMES -> matchLR(to, left, right, idx).match(
      standard = { cmd(IMUL, l, r) },
      bothMem = {
        cmd(MOV, EAX, l)
        cmd(IMUL, EAX, r)
        cmd(MOV, l, EAX)
      },
      leftMem = {
        cmd(MOV, EAX, l)
        cmd(IMUL, EAX, r)
        cmd(MOV, l, EAX)
      },
    )
    NumOp.DIVIDE -> cdqIDiv(to, left, right, EAX, idx)
    NumOp.MOD -> cdqIDiv(to, left, right, EDX, idx)
  }

  private fun cdqIDiv(to: VirtualReg, left: ValueHolder, right: ValueHolder, from: Reg, idx: StmtIdx) {
    cmd(MOV, EAX, left.get())

    preserveReg(ECX, EDX, at = idx) {
      val by = when (val by = right.get()) {
        EDX -> cmd(MOV, ECX, EDX).then { ECX }
        is Imm -> cmd(MOV, ECX, by).then { ECX }
        is Mem -> by
        is Reg -> by
      }
      cmd(CDQ)
      cmd(IDIV, by)
      assign(to, from, idx)
    }
  }

  private fun preserveReg(vararg reg: Reg, at: StmtIdx, action: () -> Unit) {
    val preserve = analysis.aliveOver[at].filter { alive -> reg.any { it == alive.get() } }
    preserve.forEach { cmd(PUSH, it.get()) }
    action()
    preserve.asReversed().forEach { cmd(POP, it.get()) }
  }

  private fun UnOp.on(to: VirtualReg, from: ValueHolder, idx: StmtIdx): Unit = when (this) {
    UnOp.NEG -> assign(to, from.get(), idx).then { cmd(NEG, to.get()) }
    UnOp.NOT -> {
      val label = labelGenerator()
      assign(to, 0.imm, idx)
      CondJumpQ(from, label).compile(idx)
      assign(to, 1.imm, idx)
      label.insert()
    }
  }

  private fun assign(to: VirtualReg, from: VarLoc, idx: StmtIdx) {
    if (to !in analysis.aliveAfter[idx]) return
    val toLoc = to.get()
    when {
      from == 0.imm && toLoc is Reg -> cmd(XOR, toLoc, toLoc)
      else -> {
        val value = when (from) {
          is Reg -> from
          is Imm -> from
          is Mem -> if (toLoc is Reg) from else cmd(MOV, EAX, from).then { EAX }
        }
        cmd(MOV, toLoc, value, value != toLoc)
      }
    }
  }

  private fun cmd(op: Named, cond: Boolean = true): Unit =
    if (cond) result.append(indent).appendLine(op.repr).unit() else Unit

  private fun cmd(op: Named, arg: Named, cond: Boolean = true): Unit =
    if (cond) result.append(indent).append(op.repr).append(" ").appendLine(arg.repr).unit()
    else Unit

  private fun cmd(op: Named, arg: Named, anyArg: Named, cond: Boolean = true): Unit =
    if (cond) result.append(indent).append(op.repr).append(" ").append(arg.repr)
      .append(", ").appendLine(anyArg.repr).unit() else Unit

  private fun Label.insert(): Unit = result.append(name).appendLine(':').unit()

  private fun ValueHolder.get(): VarLoc = allocator.get(this, strings)

  private fun ValueHolder.vTable(): VirtualTable = vTables[type] ?: err("Not defined virtual table for $$type}")

  private data class LR(val l: VarLoc, val r: VarLoc, val rev: Boolean = false) {
    fun <V> match(
      standard: LR.() -> V,
      bothMem: LR.() -> V,
      leftImm: LR.() -> V = { err("Unexpected case for $l and $r") },
      bothImm: LR.() -> V = { err("Unexpected case for $l and $r") },
      leftMem: (LR.() -> V)? = null,
    ): V = when {
      l is Reg && r is Reg -> this.standard()
      l is Reg && r is Mem -> this.standard()
      l is Reg && r is Imm -> this.standard()
      l is Mem && r is Mem -> this.bothMem()
      l is Mem && r is Reg -> this.(leftMem ?: standard)()
      l is Mem && r is Imm -> this.(leftMem ?: standard)()
      l is Imm && r is Imm -> this.bothImm()
      l is Imm && r is Reg -> this.leftImm()
      l is Imm && r is Mem -> this.leftImm()
      else -> err("Unexpected case for $l and $r")
    }
  }

  private fun matchLR(to: VirtualReg, left: ValueHolder, right: ValueHolder, idx: StmtIdx): LR {
    val resLoc = to.get()
    val lLoc = left.get()
    val rLoc = right.get()
    return when (resLoc) {
      lLoc -> LR(lLoc, rLoc)
      rLoc -> LR(rLoc, lLoc, rev = true)
      else -> LR(resLoc, rLoc).also { assign(to, lLoc, idx) }
    }
  }
}

private const val indent: String = "  "

private fun err(message: String): Nothing = throw AsmBuildException(message.msg)

private inline val Named.repr: String get() = name

private val Int.imm: Imm get() = Imm("$this", IntType)
