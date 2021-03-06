package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span

enum class UnOp : AstNode {
  NOT, NEG;

  override val span: Span? = null
}

enum class UnOpMod : AstNode {
  INC, DEC;

  override val span: Span? = null
}

sealed interface BinOp : AstNode {
  val name: String
}

enum class NumOp : BinOp {
  PLUS, MINUS, TIMES, DIV, MOD;

  override val span: Span? = null
}

enum class RelOp : BinOp {
  LT, LE, GT, GE, EQ, NE;

  override val span: Span? = null
  val rev: RelOp
    get() = when (this) {
      LT -> GE
      LE -> GT
      GT -> LE
      GE -> LT
      EQ -> NE
      NE -> EQ
    }

  val symmetric: RelOp
    get() = when (this) {
      LT -> GT
      LE -> GE
      GT -> LT
      GE -> LE
      EQ -> EQ
      NE -> NE
    }
}

enum class BooleanOp : BinOp {
  AND, OR;

  override val span: Span? = null
}
