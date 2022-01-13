package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.forEachPairIndexed

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
  is UnOpModQ -> UnOpModSubExpr(this)
  is LoadQ -> LoadQSubExpr(this)
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

private class UnOpModSubExpr(unOpModQ: UnOpModQ) : SubExpr {
  override val definedBy: VirtualReg = unOpModQ.to
  val op: UnOpMod = unOpModQ.op
  val from: ValueHolder = unOpModQ.from

  override fun hashCode(): Int = setOf(op, from).hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? UnOpModSubExpr)?.let { it.op == op && it.from == from } ?: false
}

private class LoadQSubExpr(loadQ: LoadQ) : SubExpr {
  override val definedBy: VirtualReg = loadQ.to
  val from: ValueHolder = loadQ.from
  val offset: Bytes = loadQ.offset

  override fun hashCode(): Int = setOf(offset, from).hashCode()
  override fun equals(other: Any?): Boolean =
    (other as? LoadQSubExpr)?.let { it.offset == offset && it.from == from } ?: false
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
  val aliveAfter = GlobalFlowAnalyzer.analyzeToGraph(this).aliveAfter
  for (block in block.values) {
    val remove = HashSet<Int>()
    val redefined = HashMap<Int, Quadruple>()
    block.statements.forEachPairIndexed collect@{ idx, prevStmt, stmt ->
      if (prevStmt !is DefiningVar) return@collect
      if (stmt !is AssignQ) return@collect
      if (prevStmt.to != stmt.from) return@collect
      if (prevStmt.to in aliveAfter[idx at block]) return@collect
      redefined[idx - 1] = prevStmt.redefine(stmt.to)
      remove += idx
    }
    block.mapStatements { idx, stmt ->
      when (idx) {
        in redefined -> redefined[idx]
        in remove -> null
        else -> stmt
      }
    }
  }
}

private fun FunctionCFG.propagateConstants(): Int {
  val constants = HashMap<VirtualReg, ConstValue>()
  for (block in block.values) for (stmt in block.statements)
    if (stmt is AssignQ && stmt.from is ConstValue) constants[stmt.to] = stmt.from
  var propagated = 0
  for (block in block.values) {
    block.phony.forEach { it.propagateConstants(constants) }
    block.mapStatements { _, stmt ->
      stmt.propagateConstants(constants).also { if (it != stmt) propagated += 1 }
    }
  }

  return 0
}
