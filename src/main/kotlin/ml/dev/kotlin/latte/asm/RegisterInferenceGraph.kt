package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.FlowAnalysis
import ml.dev.kotlin.latte.quadruple.VirtualReg
import ml.dev.kotlin.latte.quadruple.definedVars
import ml.dev.kotlin.latte.util.*
import java.util.*

class RegisterInferenceGraph(
  flowAnalysis: FlowAnalysis,
) : UndirectedGraph<VirtualReg>() {

  override val nodes: Set<VirtualReg> = flowAnalysis.statements.flatMapTo(HashSet()) { it.definedVars() }
  private val connected: DefaultMap<VirtualReg, Set<VirtualReg>> =
    MutableDefaultMap(withSet<VirtualReg, VirtualReg>()).also { graph ->
      fun Set<VirtualReg>.addEdges() {
        for (b in this) for (e in this) if (b != e) graph[b] += e
      }
      flowAnalysis.aliveBefore.values.forEach { it.addEdges() }
      flowAnalysis.aliveAfter.values.forEach { it.addEdges() }
    }

  val coloring by lazy { GraphColoring(setOf(), Reg.EAX, this, this::splitHeuristics, this::colorSelectHeuristics) }

  override fun connected(v: VirtualReg): Set<VirtualReg> = connected[v]

  private fun splitHeuristics(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg {
    return withEdges.values.first().first()
  }

  private fun colorSelectHeuristics(available: Set<Reg>): Reg {
    return available.first()
  }
}

