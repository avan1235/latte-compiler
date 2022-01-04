package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Cmd.*
import ml.dev.kotlin.latte.asm.Reg.*
import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.NumOp
import ml.dev.kotlin.latte.syntax.SIZE_BYTES
import ml.dev.kotlin.latte.syntax.UnOp
import ml.dev.kotlin.latte.util.AsmBuildException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.unit

internal class FunctionCompiler(
  private val blocks: List<BasicBlock>,
  private val strings: Map<String, Label>,
  private val result: StringBuilder,
  private val labelGenerator: () -> Label
) {
  private var locals: Int = 0
  private val locations = HashMap<String, VarLocation>()

  internal fun compile() {
    blocks.forEach { it.reserveVariables() }
    val optimizedStatements = blocks.asSequence().flatMap { block -> block.statements }
      .peepHoleOptimize().toList()
    optimizedStatements.forEach { it.compile() }
  }

  private fun BasicBlock.reserveVariables() {
    statements.forEach { stmt -> stmt.definedVars().forEach { it.reserve() } }
  }

  private fun VirtualReg.reserve(): Unit = when (this) {
    is ArgValue -> Arg(idx * SIZE_BYTES).let { locations[argName] = it }
    is LocalValue -> Local(locals.also { locals = it + 1 } * SIZE_BYTES).let { locations[reg] = it }
  }

  private fun ValueHolder.get(): VarLocation = when (this) {
    is ArgValue -> locations[argName]
    is LocalValue -> locations[reg]
    is BooleanConstValue -> Literal(if (bool) "1" else "0")
    is IntConstValue -> Literal("$int")
    is StringConstValue -> Literal(strings[str]?.name ?: err("Used not labeled string $this"))
  } ?: err("Used not defined variable $this")

  private fun Quadruple.compile(): Unit = when (this@compile) {
    is AssignQ -> {
      cmd(MOV, EAX, from.get())
      cmd(MOV, to.get(), EAX)
    }
    is FunCallQ -> {
      args.asReversed().forEach { cmd(PUSH, it.get()) }
      cmd(CALL, label)
      args.size.takeIf { it > 0 }?.let { cmd(ADD, ESP, it * SIZE_BYTES) }
      cmd(MOV, to.get(), EAX)
    }
    is RelCondJumpQ -> {
      cmd(MOV, EAX, left.get())
      cmd(MOV, ECX, right.get())
      cmd(CMP, EAX, ECX)
      cmd(op.jump, toLabel)
    }
    is CondJumpQ -> {
      cmd(MOV, EAX, cond.get())
      cmd(CMP, EAX, 0)
      cmd(JNE, toLabel)
    }
    is JumpQ -> cmd(JMP, toLabel)
    is FunCodeLabelQ -> {
      label.insert()
      cmd(PUSH, EBP)
      cmd(MOV, EBP, ESP)
      cmd(SUB, ESP, locals * SIZE_BYTES, locals > 0)
    }
    is RetQ -> {
      value?.let { cmd(MOV, EAX, it.get()) }
      cmd(MOV, ESP, EBP, locals > 0)
      cmd(POP, EBP)
      cmd(RET)
    }
    is CodeLabelQ -> label.insert()
    is UnOpQ -> op.on(to, from)
    is BinOpQ -> op.on(to, left, right)
    is UnOpModQ -> {
      cmd(MOV, EAX, from.get())
      cmd(op.cmd, EAX)
      cmd(MOV, to.get(), EAX)
    }
    is PhonyQ -> err("Unexpected Phony $this found on compilation phase")
  }

  private fun NumOp.on(to: VirtualReg, left: VirtualReg, right: ValueHolder): Unit = when (this) {
    NumOp.PLUS -> {
      cmd(MOV, EAX, left.get())
      cmd(ADD, EAX, right.get())
      cmd(MOV, to.get(), EAX)
    }
    NumOp.MINUS -> {
      cmd(MOV, EAX, left.get())
      cmd(SUB, EAX, right.get())
      cmd(MOV, to.get(), EAX)
    }
    NumOp.TIMES -> {
      cmd(MOV, EAX, left.get())
      cmd(IMUL, EAX, right.get())
      cmd(MOV, to.get(), EAX)
    }
    NumOp.DIVIDE -> {
      cmd(MOV, EAX, left.get())
      cmd(MOV, ECX, right.get())
      cmd(CDQ)
      cmd(IDIV, ECX)
      cmd(MOV, to.get(), EAX)
    }
    NumOp.MOD -> {
      cmd(MOV, EAX, left.get())
      cmd(MOV, ECX, right.get())
      cmd(CDQ)
      cmd(IDIV, ECX)
      cmd(MOV, to.get(), EDX)
    }
  }

  private fun UnOp.on(to: VirtualReg, from: VirtualReg): Unit = when (this) {
    UnOp.NEG -> {
      cmd(MOV, EAX, from.get())
      cmd(NEG, EAX)
      cmd(MOV, to.get(), EAX)
    }
    UnOp.NOT -> {
      val label = labelGenerator()
      cmd(XOR, ECX, ECX)
      cmd(CMP, from.get(), 0)
      cmd(JNE, label)
      cmd(MOV, ECX, 1)
      label.insert()
      cmd(MOV, to.get(), ECX)
    }
  }

  private fun cmd(op: Named, cond: Boolean = true): Unit =
    if (cond) result.append(indent).appendLine(op.name).unit() else Unit

  private fun cmd(op: Named, arg: Named, cond: Boolean = true): Unit =
    if (cond) result.append(indent).append(op.name).append(" ").appendLine(arg.name).unit() else Unit

  private fun cmd(op: Named, arg: Named, anyArg: Any, cond: Boolean = true): Unit =
    if (cond) result.append(indent).append(op.name).append(" ").append(arg.name)
      .append(", ").appendLine(anyArg.repr).unit() else Unit

  private fun Label.insert(): Unit = result.append(name).appendLine(':').unit()
}

private const val indent: String = "  "

private fun err(message: String): Nothing = throw AsmBuildException(message.msg)

private inline val ArgValue.argName get() = "@A$idx"

private inline val Any.repr: String
  get() = when (this) {
    is Named -> name
    is String -> this
    is Int -> toString()
    else -> err("No representation for $this")
  }
