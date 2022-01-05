package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Reg.EBP
import ml.dev.kotlin.latte.quadruple.Named
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.LatteIllegalStateException
import ml.dev.kotlin.latte.util.msg

sealed interface VarLoc : Named
sealed interface Mem : VarLoc

data class Imm(private val value: String, private val type: Type) : VarLoc {
  override val name = "${type.wordSize} $value"
}

data class Arg(private val offset: Int, private val type: Type) : Mem {
  override val name = "${type.wordSize} [$EBP + ${ARG_OFFSET + offset}]"
}

data class Loc(private val offset: Int, private val type: Type) : Mem {
  override val name = "${type.wordSize} [$EBP - ${LOCAL_OFFSET + offset}]"
}

private const val ARG_OFFSET: Int = 8
private const val LOCAL_OFFSET: Int = 4

private inline val Type.wordSize: String
  get() = when (this.size) {
    4 -> "DWORD"
    else -> throw LatteIllegalStateException("Used type with word size other than 4 bytes".msg)
  }
