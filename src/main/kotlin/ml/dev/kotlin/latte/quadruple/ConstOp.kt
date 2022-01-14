package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.NumOp
import ml.dev.kotlin.latte.syntax.RelOp
import ml.dev.kotlin.latte.syntax.UnOp
import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg

inline val String.label get() = Label(this)
inline val String.int get() = IntConstValue(toIntOrNull() ?: throw IRException("Int value doesn't into memory".msg))
inline val Boolean.bool get() = BooleanConstValue(this)

fun BinOpQ.constSimplify(): ValueHolder? = when {
  left is IntConstValue && right is IntConstValue -> op.num(left, right)
  right is IntConstValue && right.int == 0 && (op == NumOp.PLUS || op == NumOp.MINUS) -> left
  right is IntConstValue && right.int == 1 && (op == NumOp.TIMES || op == NumOp.DIV) -> left
  left is IntConstValue && left.int == 0 && op == NumOp.PLUS -> right
  left is IntConstValue && left.int == 1 && op == NumOp.TIMES -> right
  else -> null
}

fun UnOpQ.constSimplify(): ValueHolder? = when {
  from is IntConstValue && op == UnOp.NEG -> IntConstValue(-from.int)
  from is BooleanConstValue && op == UnOp.NOT -> BooleanConstValue(!from.bool)
  else -> null
}

fun RelCondJumpQ.constSimplify(): BooleanConstValue? = when {
  left is IntConstValue && right is IntConstValue -> op.rel(left, right)
  left is BooleanConstValue && right is BooleanConstValue -> op.rel(left, right)
  else -> null
}

fun RelOp.rel(lv: IntConstValue, rv: IntConstValue): BooleanConstValue = when (this) {
  RelOp.LT -> (lv < rv).bool
  RelOp.LE -> (lv <= rv).bool
  RelOp.GT -> (lv > rv).bool
  RelOp.GE -> (lv >= rv).bool
  RelOp.EQ -> (lv == rv).bool
  RelOp.NE -> (lv != rv).bool
}

fun RelOp.rel(lv: BooleanConstValue, rv: BooleanConstValue): BooleanConstValue = when (this) {
  RelOp.LT -> (lv < rv).bool
  RelOp.LE -> (lv <= rv).bool
  RelOp.GT -> (lv > rv).bool
  RelOp.GE -> (lv >= rv).bool
  RelOp.EQ -> (lv == rv).bool
  RelOp.NE -> (lv != rv).bool
}

fun NumOp.num(lv: IntConstValue, rv: IntConstValue): IntConstValue = when (this) {
  NumOp.PLUS -> IntConstValue(lv.int + rv.int)
  NumOp.MINUS -> IntConstValue(lv.int - rv.int)
  NumOp.TIMES -> IntConstValue(lv.int * rv.int)
  NumOp.DIV -> IntConstValue(lv.int / rv.int)
  NumOp.MOD -> IntConstValue(lv.int % rv.int)
}

private operator fun IntConstValue.compareTo(o: IntConstValue) = int.compareTo(o.int)
private operator fun BooleanConstValue.compareTo(o: BooleanConstValue) = bool.compareTo(o.bool)
