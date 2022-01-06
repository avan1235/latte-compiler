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

val RESERVED_REGISTERS = linkedSetOf(ESP, EBP, EAX)
val CALLEE_SAVED_REGISTERS = linkedSetOf(EDI, ESI, EBX)
