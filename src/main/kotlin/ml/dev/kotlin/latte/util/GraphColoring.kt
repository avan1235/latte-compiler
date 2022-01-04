package ml.dev.kotlin.latte.util

import java.util.*

class GraphColoring<V, C>(
  private val colors: Set<C>,
  private val extraColor: C,
  private val graph: UndirectedGraph<V>,
  private val spillSelectHeuristics: (withEdges: TreeMap<Int, HashSet<V>>) -> V,
  private val colorSelectHeuristics: (available: Set<C>) -> C,
) {
  val coloring: Map<V, C> = HashMap<V, C>().also { coloring ->
    val graphSize = graph.nodes.size
    val neigh: DefaultMap<V, Set<V>> = MutableDefaultMap({ v ->
      graph.connected(v).takeUnless { v in it }
        ?: throw IllegalArgumentException("Cannot find coloring for graph with cycles self edges")
    })

    val edgesCount = HashMap(graph.nodes.associateWith { neigh[it].size })
    val withEdges = edgesCount.entries
      .groupBy(keySelector = { it.value }, valueTransform = { it.key })
      .mapValuesTo(TreeMap()) { it.value.toHashSet() }
    val stack = LinkedHashSet<SN<V>>()

    fun removeNode(of: SN<V>) {
      for (n in neigh[of.n]) {
        if (SN(n) in stack) continue
        val oldCount = edgesCount[n]!!
        val newCount = oldCount - 1
        edgesCount[n] = newCount
        withEdges[oldCount] = HashSet((withEdges[oldCount] ?: HashSet()) - n)
        withEdges[newCount] = HashSet((withEdges[newCount] ?: HashSet()) + n)
      }
    }

    while (coloring.size < graphSize) {
      while (true) {
        val (count, nodes) = withEdges.firstEntry() ?: break
        if (nodes.isEmpty()) {
          withEdges -= count
          continue
        }
        val v = if (count < colors.size) nodes.first() else break
        SN(v).also { removeNode(it) }.let { stack += it }
        withEdges[count]?.remove(v)
      }

      if (stack.size >= graphSize) stack.toList().asReversed().assignColors(neigh, coloring)
      else {
        val spill = spillSelectHeuristics(withEdges)
        val spilledCount = edgesCount[spill]!!
        SN(spill, spill = true).also { removeNode(it) }.let { stack += it }
        withEdges[spilledCount]?.remove(spill)
      }
    }
  }

  private fun List<SN<V>>.assignColors(neigh: DefaultMap<V, Set<V>>, coloring: MutableMap<V, C>): Unit =
    forEach { node ->
      if (node.spill) coloring[node.n] = extraColor
      else {
        val neighColors = coloring.keys.intersect(neigh[node.n]).mapTo(HashSet()) { coloring[it]!! }
        coloring[node.n] = colorSelectHeuristics(colors - neighColors)
      }
    }
}

private data class SN<N>(val n: N, val spill: Boolean = false) {
  override fun hashCode(): Int = n.hashCode()
  override fun equals(other: Any?): Boolean = (other as? SN<*>)?.n?.equals(n) ?: false
}


