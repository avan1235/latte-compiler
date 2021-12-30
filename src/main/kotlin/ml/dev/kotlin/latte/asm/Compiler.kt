package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.BooleanOp
import ml.dev.kotlin.latte.syntax.NumOp
import ml.dev.kotlin.latte.syntax.RelOp
import ml.dev.kotlin.latte.util.AsmBuildException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.splitAt
import ml.dev.kotlin.latte.util.unit


private data class Compiler(
  private val result: StringBuilder = StringBuilder(),
  private val usedRegisters: MutableSet<Reg> = HashSet(),
  private var locals: Int = 0,
) : Appendable by result {

  fun compile(ir: IR) {
    appendLine(EXTERN_STD_LIB_FUN)
    val functions = ir.graph.orderedBlocks().splitAt(first = { it.isStart })
    functions.forEach { it.firstOrNull()?.label?.global() }
    appendLine("section .text")
    functions.flatMap { it.asSequence() }.forEach { it.compile() }
  }

  fun BasicBlock.compile() {
    if (isStart) {
      locals = 0
      usedRegisters.clear()
    }
    instructions.forEach { it.compile() }
  }

  private fun Quadruple.compile(): Unit = when (this) {
    is AssignQ -> TODO()
    is DecQ -> TODO()
    is FunCallQ -> TODO()
    is IncQ -> TODO()
    is BiCondJumpQ -> TODO()
    is CondJumpQ -> TODO()
    is JumpQ -> TODO()
    is RetQ -> TODO()
    is CodeLabelQ -> cmd(label, ":")
    is FunCodeLabelQ -> TODO()
    is UnOpQ -> TODO()
    is BinOpQ -> TODO()//when (op) {
//      NumOp.PLUS -> plus(to, left, right)
//      is RelOp -> op.on(to, left, right)
//      is NumOp -> op.on(to, left, right)
//      is BooleanOp -> err(
//        "Unexpected Boolean Binary Operation in Compiler - " +
//          "should be transformed into lazy jumps by IR Generator: $this"
//      )
//    }
  }

  private fun RelOp.on(to: MemoryLoc, left: MemoryLoc, right: ValueHolder) {

  }

  private fun NumOp.on(to: MemoryLoc, left: MemoryLoc, right: ValueHolder) {

  }

  private fun plus(to: MemoryLoc, left: MemoryLoc, right: ValueHolder) {

  }

  private fun Label.global(): Unit = appendLine("global $name").unit()
  private fun cmd(vararg cmd: Any): Unit = when (cmd.size) {
    0 -> Unit
    1 -> appendLine(cmd[0].name()).unit()
    2 -> append(cmd[0].name()).append(" ").append(cmd[1].name()).unit()
    3 -> append(cmd[0].name()).append(" ").append(cmd[1].name()).append(", ").append(cmd[1].name()).unit()
    else -> err("Invalid usage of cmd()")
  }
}

private const val EXTERN_STD_LIB_FUN: String = """
extern error
extern printInt
extern printString
extern readInt
extern readString
"""

private data class Function(val blocks: List<BasicBlock>)

enum class Reg : Named {
  EAX,
  ECX,
  EDX,
  EBX,
  ESP,
  EBP,
  ESI,
  EDI,
}

enum class Cmd : Named {
  MOV,
  PUSH,
  POP,
  ADD,
  SUB,
  XOR,
  RET,
  CALL,
  NEG,
  TEST,
  SETE,
  IMUL,
  CDQ,
  IDIV,
}

private fun Any.name(): String = when (this) {
  is Named -> name
  else -> toString()
}

private fun err(message: String): Nothing = throw AsmBuildException(message.msg)

sealed interface VarLoc
