package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap

object FlowAnalyzer {

  fun analyze(statements: List<Quadruple>): LinearFlowAnalysis {
    val maxIdx = statements.size - 1
    val aliveBefore = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })
    val aliveAfter = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ aliveBefore[it + 1] })
    val definedAt = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })
    val usedAt = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })

    statements.asReversed().forEachIndexed { idx, stmt ->
      val realIdx = maxIdx - idx

      definedAt[realIdx] += stmt.definedVars()
      usedAt[realIdx] += stmt.usedVars()
      aliveBefore[realIdx] += (aliveAfter[realIdx] - definedAt[realIdx]) + usedAt[realIdx]
    }
    return LinearFlowAnalysis(aliveAfter, aliveBefore, definedAt, usedAt)
  }
}

typealias StmtIdx = Int

data class LinearFlowAnalysis(
  val aliveAfter: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val aliveBefore: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val definedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val usedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
)
