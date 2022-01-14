package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*

fun CFG.optimize(
  removeTempDefs: Boolean,
  propagateConstants: Boolean,
  simplifyExpr: Boolean,
  removeDeadAssignQ: Boolean,
  gcse: Boolean,
  lcse: Boolean,
): Unit = with(functions.values) {
  while (true) {
    var repeats = if (removeTempDefs) sumOf { it.removeTempDefs() } else 0
    repeats += if (propagateConstants) sumOf { it.propagateConstants() } else 0
    repeats += if (simplifyExpr) sumOf { it.simplifyExpr() } else 0
    if (repeats == 0) break
  }
  if (removeDeadAssignQ) forEach { it.removeDeadAssignQ() }
  if (gcse) forEach { it.gcse() }
  if (lcse) forEach { it.lcse() }
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

private fun FunctionCFG.gcse() {
  var optimized: Int
  do {
    optimized = 0
    val (exprIn, _) = analyzeExpr(this)
    val subExprFirst = MutableDefaultMap(withSet<SubExpr, GraphLocation>())
    for (block in block.values) block.statements.forEachIndexed { idx, stmt ->
      val subExpr = stmt.subExpr() ?: return@forEachIndexed
      val loc = idx at block
      if (subExpr !in exprIn[loc]) subExprFirst[subExpr] += loc
    }
    val firstDefinitions = subExprFirst.mapValues { it.value.singleOrNull()?.then { it.key } }
    for (block in block.values) block.mapStatements { idx, stmt ->
      val subExpr = stmt.subExpr() ?: return@mapStatements stmt
      val firstSubExpr = firstDefinitions[subExpr] ?: return@mapStatements stmt
      if (idx at block == subExprFirst[firstSubExpr].single()) return@mapStatements stmt
      AssignQ(subExpr.definedBy, firstSubExpr.definedBy).also { optimized += 1 }
    }
  } while (optimized > 0)
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

private fun analyzeExpr(cfg: FunctionCFG): AliveExprAnalysis {
  val blockSubExprs = MutableDefaultMap<Label, List<IdxSubExpr>>(default@{
    val block = cfg.block[it] ?: return@default emptyList()
    val stmts = block.statements
    stmts.mapIndexed { idx, stmt -> IdxSubExpr(idx, stmt.subExpr(), block.label, idx == 0, idx == stmts.size - 1) }
  })
  val indexedSubExprs = cfg.orderedBlocks().flatMap { blockSubExprs[it.label] }
  val pred = MutableDefaultMap<IdxSubExpr, HashSet<IdxSubExpr>>({ idx ->
    if (idx.isFirst) cfg.predecessors(idx.blockLabel).mapTo(HashSet()) { blockSubExprs[it].last() }
    else hashSetOf(blockSubExprs[idx.blockLabel][idx.idx - 1])
  })
  val exprIn = MutableDefaultMap(withSet<IdxSubExpr, SubExpr>())
  val exprOut = MutableDefaultMap(withSet<IdxSubExpr, SubExpr>()).also { exprOut ->
    indexedSubExprs.forEach { idx -> idx.subExpr?.let { exprOut[idx] += it } }
  }

  while (true) {
    val lastExprIn = exprIn.deepCopy { HashSet(it) }
    val lastExprOut = exprOut.deepCopy { HashSet(it) }
    indexedSubExprs.forEach { idx ->
      exprIn[idx] = pred[idx].map { exprOut[it] }.intersect()
      exprOut[idx] = hashSetOfNotNull(idx.subExpr).apply { addAll(exprIn[idx]) }
    }
    if (lastExprIn == exprIn && lastExprOut == exprOut) break
  }
  fun byGraphLocation(default: DefaultMap<IdxSubExpr, Set<SubExpr>>) = { loc: GraphLocation ->
    val block = blockSubExprs[loc.label]
    if (loc.stmtIdx !in block.indices) emptySet()
    else default[block[loc.stmtIdx]]
  }
  return AliveExprAnalysis(MutableDefaultMap(byGraphLocation(exprIn)), MutableDefaultMap(byGraphLocation(exprOut)))
}

private data class IdxSubExpr(
  val idx: Int,
  val subExpr: SubExpr?,
  val blockLabel: Label,
  val isFirst: Boolean,
  val isLast: Boolean,
)

data class AliveExprAnalysis(
  val exprIn: DefaultMap<GraphLocation, Set<SubExpr>>,
  val exprOut: DefaultMap<GraphLocation, Set<SubExpr>>,
)
