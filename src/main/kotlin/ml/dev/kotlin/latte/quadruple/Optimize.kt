package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*

fun CFG.optimize(
  removeTempDefs: Boolean,
  propagateConstants: Boolean,
  simplifyExpr: Boolean,
  removeDeadAssignQ: Boolean,
  lcse: Boolean,
): Unit = with(functions.values) {
  while (true) {
    var repeats = if (removeTempDefs) sumOf { it.removeTempDefs() } else 0
    repeats += if (propagateConstants) sumOf { it.propagateConstants() } else 0
    repeats += if (simplifyExpr) sumOf { it.simplifyExpr() } else 0
    if (repeats == 0) break
  }
  if (removeDeadAssignQ) forEach { it.removeDeadAssignQ() }
  if (lcse) forEach { it.lcse() }
}

private fun FunctionCFG.lcse(): Unit = block.values.forEach { while (it.lcse() > 0) Unit }

private fun BasicBlock.lcse(): Int {
  val definedAt = HashMap<SubExpr, IndexedSubExpr>()
  statements.forEachIndexed { idx, stmt ->
    val subExpr = stmt.constantSubExpr() ?: return@forEachIndexed
    if (subExpr !in definedAt) definedAt[subExpr] = IndexedSubExpr(idx, subExpr)
  }
  var optimized = 0
  mapStatements { idx, stmt ->
    val subExpr = stmt.constantSubExpr() ?: return@mapStatements stmt
    val (firstIdx, firstSubExpr) = definedAt[subExpr]!!
    if (idx <= firstIdx) stmt
    else AssignQ(subExpr.definedBy, firstSubExpr.definedBy).also { optimized += 1 }
  }
  return optimized
}

private data class IndexedSubExpr(val idx: StmtIdx, val subExpr: SubExpr)

private fun FunctionCFG.removeTempDefs(): Int {
  val aliveAfter = GlobalFlowAnalyzer.analyzeToGraph(this).aliveAfter
  var removed = 0
  for (block in block.values) {
    val phonyCount = block.phony.size
    val remove = HashSet<Int>()
    val redefined = HashMap<Int, Quadruple>()
    block.statements.forEachPairIndexed collect@{ idx, prevStmt, stmt ->
      if (prevStmt !is DefiningVar) return@collect
      if (stmt !is AssignQ) return@collect
      if (prevStmt.to != stmt.from) return@collect
      if (prevStmt.to in aliveAfter[phonyCount + idx at block]) return@collect
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
    removed += remove.size
  }
  return removed
}

private fun FunctionCFG.propagateConstants(): Int {
  var propagated: Int
  var repeats = -1
  do {
    propagated = 0
    repeats += 1
    val constants = HashMap<VirtualReg, ConstValue>()
    for (block in block.values) for (stmt in block.statements)
      if (stmt is AssignQ && stmt.from is ConstValue) constants[stmt.to] = stmt.from
    for (block in block.values) {
      block.mapPhony { _, phi ->
        phi.propagateConstants(constants).also { if (it != phi) propagated += 1 }
      }
      block.mapStatements { _, stmt ->
        stmt.propagateConstants(constants).also { if (it != stmt) propagated += 1 }
      }
    }
  } while (propagated > 0)
  return repeats
}

private fun FunctionCFG.simplifyExpr(): Int {
  var simplified: Int
  var repeats = -1
  do {
    simplified = 0
    repeats += 1
    for (block in block.values) block.mapStatements { _, stmt ->
      stmt.simplify().also { if (it != stmt) simplified += 1 }
    }
  } while (simplified > 0)
  return repeats
}

private fun Quadruple.simplify(): Quadruple = when {
  this is BinOpQ -> constSimplify()?.let { AssignQ(to, it) } ?: this
  this is UnOpQ -> constSimplify()?.let { AssignQ(to, it) } ?: this
  this is RelCondJumpQ -> constSimplify()?.let { CondJumpQ(it, toLabel) } ?: this
  else -> this
}

private fun FunctionCFG.removeDeadAssignQ() {
  val aliveAfter = GlobalFlowAnalyzer.analyzeToGraph(this).aliveAfter
  for (block in block.values) block.mapStatements { idx, stmt ->
    val phonyCount = block.phony.size
    if (stmt is AssignQ && stmt.to !in aliveAfter[phonyCount + idx at block]) null else stmt
  }
}

