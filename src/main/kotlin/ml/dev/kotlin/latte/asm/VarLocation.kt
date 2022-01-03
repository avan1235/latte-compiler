package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.Named

sealed interface VarLocation : Named

data class Literal(private val value: String) : VarLocation {
  override val name: String = "DWORD $value"
}

data class Arg(private val offset: Int) : VarLocation {
  override val name = "DWORD [EBP + ${ARG_OFFSET + offset}]"
}

data class Local(private val offset: Int) : VarLocation {
  override val name = "DWORD [EBP - ${LOCAL_OFFSET + offset}]"
}

private const val ARG_OFFSET: Int = 8
private const val LOCAL_OFFSET: Int = 4
