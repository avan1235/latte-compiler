package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap

object GlobalFlowAnalyzer {

  fun analyze(cfg: FunctionCFG): FlowAnalysis {
    val emptyByStmt = { _: IndexedStatement -> HashSet<VirtualReg>() }
    val aliveIn = MutableDefaultMap(emptyByStmt)
    val aliveOut = MutableDefaultMap(emptyByStmt)
    val use = MutableDefaultMap<IndexedStatement, HashSet<VirtualReg>>({ it.quadruple.usedVars().toHashSet() })
    val kill = MutableDefaultMap<IndexedStatement, HashSet<VirtualReg>>({ it.quadruple.definedVars().toHashSet() })

    val blockStmts = MutableDefaultMap<Label, List<IndexedStatement>>(default@{
      val block = cfg.byName[it] ?: return@default emptyList()
      val statements = block.statements.toList()
      statements.mapIndexed { idx, stmt -> IndexedStatement(idx, stmt, block.label, idx == statements.size - 1) }
    })
    val indexedStatements = cfg.orderedBlocks().flatMap { blockStmts[it.label] }
    val succ = MutableDefaultMap<IndexedStatement, HashSet<IndexedStatement>>({ stmt ->
      if (stmt.isLast) cfg.successors(stmt.blockLabel).mapTo(HashSet()) { blockStmts[it].first() }
      else hashSetOf(blockStmts[stmt.blockLabel][stmt.idx + 1])
    })

    while (true) {
      val lastIn = aliveIn.deepCopy { HashSet(it) }
      val lastOut = aliveOut.deepCopy { HashSet(it) }
      indexedStatements.forEach { indexStmt ->
        aliveIn[indexStmt] = HashSet(use[indexStmt] + (aliveOut[indexStmt] - kill[indexStmt]))
        aliveOut[indexStmt] = succ[indexStmt].flatMapTo(HashSet()) { s -> aliveIn[s] }
      }
      if (lastIn == aliveIn && lastOut == aliveOut) break
    }

    fun byStmtIdx(default: DefaultMap<IndexedStatement, Set<VirtualReg>>) =
      { idx: StmtIdx -> default[indexedStatements[idx]] }

    val aliveBefore = MutableDefaultMap(byStmtIdx(aliveIn))
    val aliveAfter = MutableDefaultMap(byStmtIdx(aliveOut))
    val definedAt = MutableDefaultMap(byStmtIdx(kill))
    val usedAt = MutableDefaultMap(byStmtIdx(use))
    return FlowAnalysis(aliveBefore, aliveAfter, definedAt, usedAt, indexedStatements.map { it.quadruple })
  }
}

private data class IndexedStatement(val idx: Int, val quadruple: Quadruple, val blockLabel: Label, val isLast: Boolean)


