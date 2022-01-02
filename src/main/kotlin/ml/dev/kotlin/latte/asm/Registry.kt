package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.Named

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
  POP,
  ADD,
  SUB,
  XOR,
  RET,
  CALL,
  INC,
  DEC,
  NEG,
  IMUL,
  CDQ,
  IDIV,
  JMP,
  CMP,
  JLE,
  JL,
  JGE,
  JG,
  JNE,
  JE,
  PUSH
}
