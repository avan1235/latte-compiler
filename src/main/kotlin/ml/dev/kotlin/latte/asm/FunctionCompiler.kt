package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Cmd.*
import ml.dev.kotlin.latte.asm.Reg.*
import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.*
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
    blocks.forEach { block -> block.statements.forEach { it.compile() } }
  }

  private fun BasicBlock.reserveVariables() {
    (statements.firstOrNull() as? FunCodeLabelQ)?.args?.forEach { it.reserve() }
    statements.forEach { it.definedVar()?.reserve() }
  }

  private fun VirtualReg.reserve(): Unit = when (this) {
    is ArgValue -> Arg(ARG_OFFSET + idx * SIZE_BYTES).let { locations[argName] = it }
    is LocalValue -> Local((locals + 1).also { locals = it } * SIZE_BYTES).let { locations[name] = it }
    is TempValue -> Local((locals + 1).also { locals = it } * SIZE_BYTES).let { locations[name] = it }
  }

  private fun ValueHolder.get(): VarLocation = when (this) {
    is ArgValue -> locations[argName]
    is LocalValue -> locations[name]
    is TempValue -> locations[name]
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
      cmd(CALL, label.name)
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
      cmd(SUB, ESP, locals * SIZE_BYTES) { locals > 0 }
    }
    is RetQ -> {
      value?.let { cmd(MOV, EAX, it.get()) }
      cmd(MOV, ESP, EBP) { locals > 0 }
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
      // TODO make inc/dec available only for defined variables not temps
      // TODO handle void type properly (only as return type)
    }
    is Phony -> err("Unexpected Phony $this found on compilation phase")
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

  private fun cmd(vararg cmd: Any, indent: String = "  ", cond: () -> Boolean = { true }): Unit =
    if (cond()) when (cmd.size) {
      in 0..2 -> cmd.joinTo(result, separator = " ", prefix = indent, postfix = "\n") { it.repr }.unit()
      3 -> result.append(indent).append(cmd[0].repr).append(" ").append(cmd[1].repr)
        .append(", ").appendLine(cmd[2].repr).unit()
      else -> err("Invalid usage of cmd()")
    } else Unit

  private fun Label.insert(): Unit = result.append(repr).appendLine(':').unit()
}


private fun err(message: String): Nothing = throw AsmBuildException(message.msg)

private sealed interface VarLocation : Named
private data class Literal(val value: String) : VarLocation {
  override val name: String = "DWORD $value"
}
private data class Arg(val offset: Int) : VarLocation {
  override val name = "DWORD [EBP + $offset]"
}

private data class Local(val offset: Int) : VarLocation {
  override val name = "DWORD [EBP - $offset]"
}

private const val ARG_OFFSET: Int = 8

private inline val ArgValue.argName get() = "@A$idx"

private inline val Any.repr
  get() = when (this) {
    is Named -> name
    is String -> this
    is Int -> toString()
    else -> err("No representation for $this")
  }

private inline val RelOp.jump: Cmd
  get() = when (this) {
    RelOp.LT -> JL
    RelOp.LE -> JLE
    RelOp.GT -> JG
    RelOp.GE -> JGE
    RelOp.EQ -> JE
    RelOp.NE -> JNE
  }

private inline val UnOpMod.cmd: Cmd
  get() = when (this) {
    UnOpMod.INC -> INC
    UnOpMod.DEC -> DEC
  }


