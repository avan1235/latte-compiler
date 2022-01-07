package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.FlowAnalysis
import ml.dev.kotlin.latte.quadruple.VirtualReg
import ml.dev.kotlin.latte.quadruple.definedVars
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.UndirectedGraph
import ml.dev.kotlin.latte.util.withSet

class RegisterInferenceGraph(analysis: FlowAnalysis) : UndirectedGraph<VirtualReg>() {

  override val nodes: Set<VirtualReg> = analysis.statements.flatMapTo(HashSet()) { it.definedVars() }

  private val connected: DefaultMap<VirtualReg, Set<VirtualReg>> = with(analysis) {
    MutableDefaultMap(withSet<VirtualReg, VirtualReg>()).also { graph ->
      fun Set<VirtualReg>.addEdges(): Unit = forEach { b -> forEach { e -> if (b != e) graph[b] += e } }
      analysis.statements.indices
        .onEach { aliveBefore[it].addEdges() }
        .onEach { aliveAfter[it].addEdges() }
    }
  }

  override fun connected(v: VirtualReg): Set<VirtualReg> = connected[v]
}
