package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*

fun Quadruple.constantSubExpr(): SubExpr? = when (this) {
  is BinOpQ -> BinOpSubExpr(this)
  is UnOpQ -> UnOpSubExpr(this)
  is UnOpModQ -> UnOpModSubExpr(this)
  else -> null
}

sealed interface SubExpr {
  val definedBy: VirtualReg
}

class UnOpSubExpr(unOpQ: UnOpQ) : SubExpr {
  override val definedBy: VirtualReg = unOpQ.to
  val op: UnOp = unOpQ.op
  val from: ValueHolder = unOpQ.from

  override fun hashCode(): Int = setOf(op, from).hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? UnOpSubExpr)?.let { it.op == op && it.from == from } ?: false
}

class UnOpModSubExpr(unOpModQ: UnOpModQ) : SubExpr {
  override val definedBy: VirtualReg = unOpModQ.to
  val op: UnOpMod = unOpModQ.op
  val from: ValueHolder = unOpModQ.from

  override fun hashCode(): Int = setOf(op, from).hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? UnOpModSubExpr)?.let { it.op == op && it.from == from } ?: false
}

class BinOpSubExpr(binOpQ: BinOpQ) : SubExpr {
  override val definedBy: VirtualReg = binOpQ.to
  val left: ValueHolder = binOpQ.left
  val op: BinOp = binOpQ.op
  val right: ValueHolder = binOpQ.right

  override fun hashCode(): Int = setOf(left, right, op).hashCode()
  override fun equals(other: Any?): Boolean {
    if (other !is BinOpSubExpr) return false
    val thisUnordered = setOf(left, right)
    val otherUnordered = setOf(other.left, other.right)
    val thisOrdered = listOf(left, right)
    val otherOrdered = listOf(other.left, other.right)
    val otherRevOrdered = listOf(other.right, other.left)
    return when {
      op == NumOp.PLUS && other.op == NumOp.PLUS && thisUnordered == otherUnordered -> true
      op == NumOp.TIMES && other.op == NumOp.TIMES && thisUnordered == otherUnordered -> true
      op == NumOp.MINUS && other.op == NumOp.MINUS && thisOrdered == otherOrdered -> true
      op == NumOp.DIV && other.op == NumOp.DIV && thisOrdered == otherOrdered -> true
      op == NumOp.MOD && other.op == NumOp.MOD && thisOrdered == otherOrdered -> true
      op is RelOp && other.op is RelOp -> when {
        op == other.op && thisOrdered == otherOrdered -> true
        op == other.op.symmetric && thisOrdered == otherRevOrdered -> true
        else -> false
      }
      op == BooleanOp.AND && other.op == BooleanOp.AND && thisUnordered == otherUnordered -> true
      op == BooleanOp.OR && other.op == BooleanOp.OR && thisUnordered == otherUnordered -> true
      else -> false
    }
  }
}
