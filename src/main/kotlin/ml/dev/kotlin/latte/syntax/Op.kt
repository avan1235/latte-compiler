package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span

enum class UnOp : AstNode {
  Not, Neg;

  override val span: Span? = null
}

sealed interface BinOp : AstNode
enum class NumOp : BinOp {
  Plus, Minus, Times, Divide, Mod;

  override val span: Span? = null
}

enum class RelOp : BinOp {
  LT, LE, GT, GE, EQ, NE;

  override val span: Span? = null
}

enum class BooleanOp : BinOp {
  And, Or;

  override val span: Span? = null
}
