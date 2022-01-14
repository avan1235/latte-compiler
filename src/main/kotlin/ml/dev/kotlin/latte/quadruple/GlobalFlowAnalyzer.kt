package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.withSet

object GlobalFlowAnalyzer {

  fun analyzeToLinear(cfg: FunctionCFG): FlowAnalysis {
    val analysis = analyze(cfg)

    fun byStmtIdx(default: DefaultMap<IdxStmt, Set<VirtualReg>>) =
      { idx: StmtIdx -> default[analysis.indexedStmts[idx]] }

    val aliveBefore = MutableDefaultMap(byStmtIdx(analysis.aliveBefore))
    val aliveAfter = MutableDefaultMap(byStmtIdx(analysis.aliveAfter))
    val definedAt = MutableDefaultMap(byStmtIdx(analysis.definedAt))
    val usedAt = MutableDefaultMap(byStmtIdx(analysis.usedAt))
    return FlowAnalysis(aliveBefore, aliveAfter, definedAt, usedAt, analysis.indexedStmts.map { it.quadruple })
  }

  fun analyzeToGraph(cfg: FunctionCFG): GraphFlowAnalysis {
    val analysis = analyze(cfg)

    fun byGraphLocation(default: DefaultMap<IdxStmt, Set<VirtualReg>>) = { loc: GraphLocation ->
      val block = analysis.blockStmts[loc.label]
      if (loc.stmtIdx !in block.indices) emptySet()
      else default[block[loc.stmtIdx]]
    }

    val aliveBefore = MutableDefaultMap(byGraphLocation(analysis.aliveBefore))
    val aliveAfter = MutableDefaultMap(byGraphLocation(analysis.aliveAfter))
    val definedAt = MutableDefaultMap(byGraphLocation(analysis.definedAt))
    val usedAt = MutableDefaultMap(byGraphLocation(analysis.usedAt))
    return GraphFlowAnalysis(aliveBefore, aliveAfter, definedAt, usedAt, cfg)
  }

  private fun analyze(cfg: FunctionCFG): AnalysisResult {
    val aliveIn = MutableDefaultMap(withSet<IdxStmt, VirtualReg>())
    val aliveOut = MutableDefaultMap(withSet<IdxStmt, VirtualReg>())
    val use = MutableDefaultMap<IdxStmt, HashSet<VirtualReg>>({ it.quadruple.usedVars().toHashSet() })
    val kill = MutableDefaultMap<IdxStmt, HashSet<VirtualReg>>({ it.quadruple.definedVars().toHashSet() })

    val blockStmts = MutableDefaultMap<Label, List<IdxStmt>>(default@{
      val block = cfg.block[it] ?: return@default emptyList()
      val statements = block.statementsWithPhony.toList()
      statements.mapIndexed { idx, stmt -> IdxStmt(idx, stmt, block.label, idx == statements.size - 1) }
    })
    val indexedStmts = cfg.orderedBlocks().flatMap { blockStmts[it.label] }
    val succ = MutableDefaultMap<IdxStmt, HashSet<IdxStmt>>({ stmt ->
      if (stmt.isLast) cfg.successors(stmt.blockLabel).mapTo(HashSet()) { blockStmts[it].first() }
      else hashSetOf(blockStmts[stmt.blockLabel][stmt.idx + 1])
    })

    while (true) {
      val lastIn = aliveIn.deepCopy { HashSet(it) }
      val lastOut = aliveOut.deepCopy { HashSet(it) }
      indexedStmts.forEach { indexStmt ->
        aliveIn[indexStmt] = HashSet(use[indexStmt] + (aliveOut[indexStmt] - kill[indexStmt]))
        aliveOut[indexStmt] = succ[indexStmt].flatMapTo(HashSet()) { s -> aliveIn[s] }
      }
      if (lastIn == aliveIn && lastOut == aliveOut) break
    }

    return AnalysisResult(aliveIn, aliveOut, kill, use, indexedStmts, blockStmts)
  }
}

private data class AnalysisResult(
  val aliveBefore: DefaultMap<IdxStmt, Set<VirtualReg>>,
  val aliveAfter: DefaultMap<IdxStmt, Set<VirtualReg>>,
  val definedAt: DefaultMap<IdxStmt, Set<VirtualReg>>,
  val usedAt: DefaultMap<IdxStmt, Set<VirtualReg>>,
  val indexedStmts: List<IdxStmt>,
  val blockStmts: DefaultMap<Label, List<IdxStmt>>
)

private data class IdxStmt(val idx: Int, val quadruple: Quadruple, val blockLabel: Label, val isLast: Boolean)

data class GraphLocation(val label: Label, val stmtIdx: StmtIdx)

infix fun StmtIdx.at(block: BasicBlock): GraphLocation = GraphLocation(block.label, this)

data class GraphFlowAnalysis(
  val aliveBefore: DefaultMap<GraphLocation, Set<VirtualReg>>,
  val aliveAfter: DefaultMap<GraphLocation, Set<VirtualReg>>,
  val definedAt: DefaultMap<GraphLocation, Set<VirtualReg>>,
  val usedAt: DefaultMap<GraphLocation, Set<VirtualReg>>,
  val graph: FunctionCFG,
)
