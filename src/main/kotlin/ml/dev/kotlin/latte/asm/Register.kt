package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.asm.Reg.*
import ml.dev.kotlin.latte.quadruple.Named

enum class Reg : Named, VarLoc {
  EAX,
  EBX,
  ECX,
  EDX,
  ESP,
  EBP,
  EDI,
  ESI,
}

val RESERVED_REGISTERS = setOf(ESP, EBP, EBX)
val CALLER_SAVED_REGISTERS = setOf(EAX, EDX, ECX)
val CALLEE_SAVED_REGISTERS = setOf(EDI, ESI)
