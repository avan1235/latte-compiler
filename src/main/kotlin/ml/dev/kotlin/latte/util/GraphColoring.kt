package ml.dev.kotlin.latte.util

import java.util.*

class GraphColoring<V, Common, Colored : Common, Default : Common>(
  private val graph: UndirectedGraph<V>,
  private val colors: Set<Colored>,
  private val strategy: GraphColoringStrategy<V, Common, Colored, Default>,
) {
  val coloring: Map<V, Common> = HashMap<V, Common>().also { coloring ->
    val graphSize = graph.nodes.size
    val neigh: DefaultMap<V, Set<V>> = MutableDefaultMap({ v ->
      graph.connected(v).takeUnless { v in it }
        ?: throw LatteIllegalStateException("Cannot find coloring for graph with cycles self edges".msg)
    })

    val edgesCount = HashMap(graph.nodes.associateWith { neigh[it].size })
    val withEdges = edgesCount.entries
      .groupBy(keySelector = { it.value }, valueTransform = { it.key })
      .mapValuesTo(TreeMap()) { it.value.toHashSet() }
    val stack = LinkedHashSet<StackNode<V>>()

    fun removeNode(of: StackNode<V>) {
      for (n in neigh[of.n]) {
        if (StackNode(n) in stack) continue
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
        StackNode(v).also { removeNode(it) }.let { stack += it }
        withEdges[count]?.remove(v)
      }

      if (stack.size >= graphSize) stack.toList().asReversed().assignColors(neigh, coloring)
      else {
        val spill = strategy.spillSelectHeuristics(withEdges)
        val spilledCount = edgesCount[spill]!!
        StackNode(spill, spill = true).also { removeNode(it) }.let { stack += it }
        withEdges[spilledCount]?.remove(spill)
      }
    }
  }

  private fun List<StackNode<V>>.assignColors(neigh: DefaultMap<V, Set<V>>, coloring: MutableMap<V, Common>): Unit =
    forEach { node ->
      if (node.spill) coloring[node.n] = strategy.extraColor(node.n)
      else {
        val neighColors = coloring.keys.intersect(neigh[node.n]).mapTo(HashSet()) { coloring[it]!! }
        val candidates = colors.filterNotTo(HashSet()) { neighColors.contains<Common>(it) }
        coloring[node.n] = strategy.colorSelectHeuristics(node.n, candidates, coloring)
      }
    }
}

interface GraphColoringStrategy<V, Common, Colored : Common, Default : Common> {
  fun extraColor(v: V): Default
  fun spillSelectHeuristics(withEdges: TreeMap<Int, HashSet<V>>): V
  fun colorSelectHeuristics(node: V, available: Set<Colored>, coloring: Map<V, Common>): Colored
}

inline fun <V, Common, Colored : Common, Default : Common> graphColoringStrategy(
  crossinline extraColor: (V) -> Default,
  crossinline spillSelectHeuristics: (withEdges: TreeMap<Int, HashSet<V>>) -> V,
  crossinline colorSelectHeuristics: (node: V, available: Set<Colored>, coloring: Map<V, Common>) -> Colored,
): GraphColoringStrategy<V, Common, Colored, Default> = object : GraphColoringStrategy<V, Common, Colored, Default> {
  override fun extraColor(v: V): Default = extraColor(v)
  override fun spillSelectHeuristics(withEdges: TreeMap<Int, HashSet<V>>): V = spillSelectHeuristics(withEdges)
  override fun colorSelectHeuristics(node: V, available: Set<Colored>, coloring: Map<V, Common>): Colored =
    colorSelectHeuristics(node, available, coloring)
}

private data class StackNode<N>(val n: N, val spill: Boolean = false) {
  override fun hashCode(): Int = n.hashCode()
  override fun equals(other: Any?): Boolean = (other as? StackNode<*>)?.n?.equals(n) ?: false
}


