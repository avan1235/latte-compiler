package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span

enum class UnOp : AstNode {
  NOT, NEG;

  override val span: Span? = null
}

sealed interface BinOp : AstNode {
  val name: String
}

enum class NumOp : BinOp {
  PLUS, MINUS, TIMES, DIVIDE, MOD;

  override val span: Span? = null
}

enum class RelOp : BinOp {
  LT, LE, GT, GE, EQ, NE;

  override val span: Span? = null
}

enum class BooleanOp : BinOp {
  AND, OR;

  override val span: Span? = null
}
