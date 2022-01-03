package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap

object FlowAnalyzer {

  fun analyze(statements: ArrayList<Quadruple>): FlowAnalysis {
    val maxIdx = statements.size - 1
    val aliveAfter = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })
    val aliveBefore = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })
    val definedAt = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })
    val usedAt = MutableDefaultMap<StmtIdx, HashSet<VirtualReg>>({ HashSet() })

    statements.asReversed().forEachIndexed { idx, stmt ->
      val realIdx = maxIdx - idx

      definedAt[realIdx] += stmt.definedVars()
      usedAt[realIdx] += stmt.usedVars()
      val inVar = (aliveAfter[realIdx] - definedAt[realIdx]) + usedAt[realIdx]

      aliveBefore[realIdx] += inVar
      aliveAfter[realIdx - 1] += inVar
    }
    return FlowAnalysis(aliveAfter, aliveBefore, definedAt, usedAt)
  }
}

typealias StmtIdx = Int

data class FlowAnalysis(
  val aliveAfter: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val aliveBefore: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val definedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val usedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
)
