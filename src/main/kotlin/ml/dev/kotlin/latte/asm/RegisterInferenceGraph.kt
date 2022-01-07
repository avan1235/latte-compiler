package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.FlowAnalysis
import ml.dev.kotlin.latte.quadruple.LocalValue
import ml.dev.kotlin.latte.quadruple.VirtualReg
import ml.dev.kotlin.latte.quadruple.definedVars
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.UndirectedGraph
import ml.dev.kotlin.latte.util.withSet

class RegisterInferenceGraph(analysis: FlowAnalysis) : UndirectedGraph<LocalValue>() {

  override val nodes: Set<LocalValue> =
    analysis.statements.flatMapTo(HashSet()) { it.definedVars().filterIsInstance<LocalValue>() }

  private val connected: DefaultMap<LocalValue, Set<LocalValue>> = with(analysis) {
    MutableDefaultMap(withSet<LocalValue, LocalValue>()).also { graph ->
      fun Set<VirtualReg>.addEdges(): Unit = filterIsInstance<LocalValue>().run {
        forEach { b -> forEach { e -> if (b != e) graph[b] += e } }
      }
      analysis.statements.indices
        .onEach { aliveBefore[it].addEdges() }
        .onEach { aliveAfter[it].addEdges() }
    }
  }

  override fun connected(v: LocalValue): Set<LocalValue> = connected[v]
}
