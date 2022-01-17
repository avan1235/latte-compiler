package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.forEachPairIndexed
import ml.dev.kotlin.latte.util.withSet
import kotlin.collections.contains
import kotlin.collections.set

fun CFG.optimize(
  removeTempDefs: Boolean,
  propagateConstants: Boolean,
  simplifyExpr: Boolean,
  removeDeadAssignQ: Boolean,
  lcse: Boolean,
  gcse: Boolean,
): Unit = with(functions.values) {
  var repeats = 0
  fun countIfEnabled(flag: Boolean, action: FunctionCFG.() -> Int) {
    repeats += if (flag) sumOf { it.action() } else 0
  }
  do {
    repeats = 0
    countIfEnabled(removeTempDefs) { removeTempDefs() }
    countIfEnabled(propagateConstants) { propagateConstants() }
    countIfEnabled(simplifyExpr) { simplifyExpr() }
    countIfEnabled(propagateConstants) { propagateConstants() }
    countIfEnabled(removeDeadAssignQ) { removeDeadAssignQ() }
    countIfEnabled(lcse) { lcse() }
    countIfEnabled(gcse) { gcse() }
  } while (repeats > 0)
}

private fun FunctionCFG.lcse(): Int = block.values.sumOf { it.lcse() }

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

private fun FunctionCFG.gcse(): Int {
  var optimized = 0
  val exprIn = GlobalFlowAnalyzer.analyzeAliveExpr(this).exprIn
  val exprFirstDefAt = MutableDefaultMap(withSet<SubExpr, GraphLocation>())
  for (block in block.values) block.statements.forEachIndexed stmt@{ idx, stmt ->
    val subExpr = stmt.constantSubExpr() ?: return@stmt
    val loc = idx at block
    if (subExpr !in exprIn[loc]) {
      exprFirstDefAt[subExpr] += loc
    }
  }
  val exprFirstDef = exprFirstDefAt.mapValues { if (it.value.size == 1) it.key else null }
  for (block in block.values) block.mapStatements stmt@{ idx, stmt ->
    val subExpr = stmt.constantSubExpr() ?: return@stmt stmt
    val firstSubExpr = exprFirstDef[subExpr] ?: return@stmt stmt
    if (idx at block == exprFirstDefAt[firstSubExpr].single()) return@stmt stmt
    AssignQ(subExpr.definedBy, firstSubExpr.definedBy).also { optimized += 1 }
  }
  return optimized
}


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
    val constants = HashMap<VirtualReg, ValueHolder>()
    for (block in block.values) for (stmt in block.statements)
      if (stmt is AssignQ) constants[stmt.to] = stmt.from
    for (block in block.values) {
      block.mapPhony { _, phi -> phi.propagateConstants(constants).also { if (it != phi) propagated += 1 } }
      block.mapStatements { _, stmt -> stmt.propagateConstants(constants).also { if (it != stmt) propagated += 1 } }
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

private fun Quadruple.simplify(): Quadruple = when (this) {
  is BinOpQ -> constSimplify()?.let { AssignQ(to, it) } ?: this
  is UnOpQ -> constSimplify()?.let { AssignQ(to, it) } ?: this
  is RelCondJumpQ -> constSimplify()?.let { CondJumpQ(it, toLabel) } ?: this
  else -> this
}

private fun FunctionCFG.removeDeadAssignQ(): Int {
  val aliveAfter = GlobalFlowAnalyzer.analyzeToGraph(this).aliveAfter
  var removed = 0
  for (block in block.values) {
    val phonyCount = block.phony.size
    block.mapStatements { idx, stmt ->
      if (stmt is AssignQ && stmt.to !in aliveAfter[phonyCount + idx at block]) {
        removed += 1
        null
      } else stmt
    }
  }
  return removed
}

