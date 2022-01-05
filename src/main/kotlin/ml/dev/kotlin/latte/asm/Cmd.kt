package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.Named
import ml.dev.kotlin.latte.syntax.RelOp
import ml.dev.kotlin.latte.syntax.UnOpMod

enum class Cmd : Named {
  MOV,
  LEA,
  POP,
  ADD,
  SUB,
  XOR,
  RET,
  LEAVE,
  CALL,
  INC,
  DEC,
  NEG,
  IMUL,
  CDQ,
  IDIV,
  JMP,
  CMP,
  TEST,
  JLE,
  JL,
  JGE,
  JG,
  JNE,
  JE,
  JZ,
  PUSH
}

inline val RelOp.jump: Cmd
  get() = when (this) {
    RelOp.LT -> Cmd.JL
    RelOp.LE -> Cmd.JLE
    RelOp.GT -> Cmd.JG
    RelOp.GE -> Cmd.JGE
    RelOp.EQ -> Cmd.JE
    RelOp.NE -> Cmd.JNE
  }

inline val UnOpMod.cmd: Cmd
  get() = when (this) {
    UnOpMod.INC -> Cmd.INC
    UnOpMod.DEC -> Cmd.DEC
  }
