package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Cmd.*
import ml.dev.kotlin.latte.asm.Reg.*
import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.NumOp
import ml.dev.kotlin.latte.syntax.UnOp
import ml.dev.kotlin.latte.util.AsmBuildException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.then
import ml.dev.kotlin.latte.util.unit

class FunctionCompiler(
  private val analysis: FlowAnalysis,
  private val strings: Map<String, Label>,
  private val result: StringBuilder,
  private val labelGenerator: () -> Label
) {
  private val memoryAllocator = MemoryAllocator(analysis)

  internal fun compile() {
    analysis.statements.forEachIndexed { idx, stmt -> stmt.compile(idx) }
  }

  private fun Quadruple.compile(idx: Int): Unit = when (this@compile) {
    is AssignQ -> assign(to, from.get(), idx)
    is FunCallQ -> {
      args.asReversed().forEach { cmd(PUSH, it.get()) }
      cmd(CALL, label)
      argsSize.takeIf { it > 0 }?.let { cmd(ADD, ESP, it.imm) }
      assign(to, EAX, idx)
    }
    is RelCondJumpQ -> {
      val left = left.get()
      val right = right.get()
      val op = when {
        left is Reg && right is Reg -> cmd(CMP, left, right).then { op }
        left is Mem && right is Reg -> cmd(CMP, left, right).then { op }
        left is Reg && right is Mem -> cmd(CMP, left, right).then { op }
        left is Reg && right is Imm -> cmd(CMP, left, right).then { op }
        left is Mem && right is Imm -> cmd(CMP, left, right).then { op }
        left is Imm && right is Reg -> cmd(CMP, right, left).then { op.rev }
        left is Imm && right is Mem -> cmd(CMP, right, left).then { op.rev }
        left is Imm && right is Imm -> cmd(MOV, EAX, left).then { cmd(CMP, EAX, right) }.then { op }
        left is Mem && right is Mem -> cmd(MOV, EAX, left).then { cmd(CMP, EAX, right) }.then { op }
        else -> err("Unexpected case for $this")
      }
      cmd(op.jump, toLabel)
    }
    is CondJumpQ -> {
      cmd(MOV, EAX, cond.get())
      cmd(TEST, EAX, EAX)
      cmd(JZ, toLabel)
    }
    is JumpQ -> cmd(JMP, toLabel)
    is FunCodeLabelQ -> {
      val offset = memoryAllocator.localsOffset
      label.insert()
      cmd(PUSH, EBP)
      cmd(MOV, EBP, ESP)
      cmd(SUB, ESP, offset.imm)
      memoryAllocator.preservedOnCall.forEach { cmd(PUSH, it) }
    }
    is RetQ -> {
      value?.let { cmd(MOV, EAX, it.get()) }
      memoryAllocator.preservedOnCall.asReversed().forEach { cmd(POP, it) }
      cmd(MOV, ESP, EBP)
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
  }

  private fun onMatch(l: VarLoc, r: VarLoc, normal: () -> Unit, bothMem: () -> Unit) {
    when {
      l is Reg && r is Reg -> normal()
      l is Mem && r is Reg -> normal()
      l is Reg && r is Mem -> normal()
      l is Reg && r is Imm -> normal()
      l is Mem && r is Imm -> normal()
      l is Mem && r is Mem -> bothMem()
      else -> err("Unexpected case for $l and $r")
    }
  }

  private data class LR(val l: VarLoc, val r: VarLoc, val rev: Boolean = false)

  private fun matchLR(to: VirtualReg, left: VirtualReg, right: ValueHolder, idx: Int): LR {
    val resLoc = to.get()
    val lLoc = left.get()
    val rLoc = right.get()
    return when (resLoc) {
      lLoc -> LR(lLoc, rLoc)
      rLoc -> LR(rLoc, lLoc, rev = true)
      else -> LR(resLoc, rLoc).also { assign(to, lLoc, idx) }
    }
  }

  private fun NumOp.on(to: VirtualReg, left: VirtualReg, right: ValueHolder, idx: Int): Unit = when (this) {
    NumOp.PLUS -> {
      val (l, r) = matchLR(to, left, right, idx)
      onMatch(
        l, r,
        normal = { cmd(ADD, l, r) },
        bothMem = {
          cmd(MOV, EAX, r)
          cmd(ADD, l, EAX)
        },
      )
    }
    NumOp.MINUS -> {
      val (l, r, rev) = matchLR(to, left, right, idx)
      onMatch(
        l, r,
        normal = {
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
    }
    NumOp.TIMES -> {
      val resLoc = to.get()
      val lLoc = left.get()
      val rLoc = right.get()

      val (l, r) = when (resLoc) {
        lLoc -> LR(lLoc, rLoc)
        rLoc -> LR(rLoc, lLoc)
        else -> LR(resLoc, rLoc).also { assign(to, lLoc, idx) }
      }

      onMatch(
        l, r,
        normal = { cmd(IMUL, l, r) },
        bothMem = {
          cmd(MOV, EAX, l)
          cmd(IMUL, EAX, r)
          cmd(MOV, l, EAX)
        },
      )
    }
    NumOp.DIVIDE -> cdqIDiv(left, right).then { assign(to, EAX, idx) }
    NumOp.MOD -> cdqIDiv(left, right).then { assign(to, EDX, idx) }
  }

  private fun cdqIDiv(left: VirtualReg, right: ValueHolder) {
    cmd(MOV, EAX, left.get())
    cmd(CDQ)
    val by = when (val by = right.get()) {
      is Imm -> cmd(MOV, ECX, by).then { ECX }
      is Mem -> by
      is Reg -> by
    }
    cmd(IDIV, by)
  }

  private fun UnOp.on(to: VirtualReg, from: VirtualReg, idx: Int): Unit = when (this) {
    UnOp.NEG -> assign(to, from.get(), idx).then { cmd(NEG, to.get()) }
    UnOp.NOT -> {
      val label = labelGenerator()
      assign(to, 0.imm, idx)
      CondJumpQ(from, label).compile(idx)
      assign(to, 1.imm, idx)
      label.insert()
    }
  }

  private fun assign(destination: VirtualReg, from: VarLoc, idx: Int) {
    if (destination !in analysis.aliveAfter[idx]) return
    val to = destination.get()
    val value = when (from) {
      is Reg -> from
      is Imm -> from
      is Mem -> if (to is Reg) from else EAX.also { cmd(MOV, EAX, from) }
    }
    cmd(MOV, to, value, value != to)
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

  private fun ValueHolder.get(): VarLoc =
    memoryAllocator.get(this, strings) { to, from -> cmd(MOV, to, from) }
}

private const val indent: String = "  "

private fun err(message: String): Nothing = throw AsmBuildException(message.msg)

private inline val Named.repr: String get() = name

private val Int.imm: Imm get() = Imm("$this", IntType)
