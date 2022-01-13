package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*

fun CFG.optimize(): Unit = with(functions.values) {
  forEach { it.removeTempDefs() }
  forEach { it.lcse() }
}

private fun FunctionCFG.lcse(): Unit = block.values.forEach { while (it.lcse() > 0) Unit }

private fun BasicBlock.lcse(): Int {
  val definedAt = HashMap<SubExpr, IndexedSubExpr>()
  statements.forEachIndexed { idx, stmt ->
    val subExpr = stmt.subExpr() ?: return@forEachIndexed
    if (subExpr !in definedAt) definedAt[subExpr] = IndexedSubExpr(idx, subExpr)
  }
  var optimized = 0
  mapStatements { idx, stmt ->
    val subExpr = stmt.subExpr() ?: return@mapStatements stmt
    val (firstIdx, firstSubExpr) = definedAt[subExpr]!!
    if (idx <= firstIdx) stmt
    else AssignQ(subExpr.definedBy, firstSubExpr.definedBy).also { optimized += 1 }
  }
  return optimized
}

private data class IndexedSubExpr(val idx: StmtIdx, val subExpr: SubExpr)

private fun Quadruple.subExpr(): SubExpr? = when (this) {
  is BinOpQ -> BinOpSubExpr(this)
  is UnOpQ -> UnOpSubExpr(this)
  else -> null
}

private sealed interface SubExpr {
  val definedBy: VirtualReg
}

private class UnOpSubExpr(unOpQ: UnOpQ) : SubExpr {
  override val definedBy: VirtualReg = unOpQ.to
  val op: UnOp = unOpQ.op
  val from: ValueHolder = unOpQ.from

  override fun hashCode(): Int = setOf(op, from).hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? UnOpSubExpr)?.let { it.op == op && it.from == from } ?: false
}

private class BinOpSubExpr(binOpQ: BinOpQ) : SubExpr {
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
      op == NumOp.DIVIDE && other.op == NumOp.DIVIDE && thisOrdered == otherOrdered -> true
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

private fun FunctionCFG.removeTempDefs() {
  val analysis = GlobalFlowAnalyzer.analyzeToGraph(this)
  for (block in block.values) {
    val statements = block.statements
    val remove = HashSet<Quadruple>()
    val redefined = HashMap<Quadruple, Quadruple>()
    for (idx in statements.indices) {
      if (idx == 0) continue
      val prevStmt = statements[idx - 1]
      val stmt = statements[idx]
      if (prevStmt !is DefiningVar) continue
      if (stmt !is AssignQ) continue
      if (prevStmt.to != stmt.from) continue
      if (prevStmt.to in analysis.aliveAfter[idx at block]) continue
      redefined[prevStmt] = prevStmt.redefine(stmt.to)
      remove += stmt
    }
    block.mapStatements { _, stmt ->
      when (stmt) {
        in redefined -> redefined[stmt]
        in remove -> null
        else -> stmt
      }
    }
  }
}
