package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap

object LocalFlowAnalyzer {

  fun analyze(
    statements: List<Quadruple>,
    aliveOut: Set<VirtualReg>,
    startIdx: Int = 0,
    aliveBefore: MutableDefaultMap<StmtIdx, HashSet<VirtualReg>> = MutableDefaultMap(generate),
    aliveAfter: MutableDefaultMap<StmtIdx, HashSet<VirtualReg>> = MutableDefaultMap(generate),
    definedAt: MutableDefaultMap<StmtIdx, HashSet<VirtualReg>> = MutableDefaultMap(generate),
    usedAt: MutableDefaultMap<StmtIdx, HashSet<VirtualReg>> = MutableDefaultMap(generate),
  ): FlowAnalysis {
    val maxIdx = statements.size - 1
    aliveAfter[startIdx + maxIdx] += aliveOut

    statements.asReversed().forEachIndexed { revIdx, stmt ->
      val idx = maxIdx - revIdx
      definedAt[startIdx + idx] += stmt.definedVars()
      usedAt[startIdx + idx] += stmt.usedVars()
      aliveBefore[startIdx + idx] += (aliveAfter[startIdx + idx] - definedAt[startIdx + idx]) + usedAt[startIdx + idx]
      aliveAfter[startIdx + idx - 1] += aliveBefore[startIdx + idx]
    }
    return FlowAnalysis(aliveBefore, aliveAfter, definedAt, usedAt, statements)
  }
}

typealias StmtIdx = Int

data class FlowAnalysis(
  val aliveBefore: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val aliveAfter: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val definedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val usedAt: DefaultMap<StmtIdx, Set<VirtualReg>>,
  val statements: List<Quadruple>,
) {
  val aliveOver: DefaultMap<StmtIdx, Set<VirtualReg>> =
    MutableDefaultMap({ aliveBefore[it].intersect(aliveAfter[it]) })
}

private val generate = { _: StmtIdx -> HashSet<VirtualReg>() }
