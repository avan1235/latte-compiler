package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GraphTest {
  @Test
  fun `should return reachable nodes from specified node`() {
    val graph = TestDirectedGraph(
      1 to 2,
      1 to 3,
      2 to 3,
      2 to 4,
      2 to 5,
      5 to 1,
      6 to 7,
    )
    val expected = setOf(1, 2, 3, 4, 5)
    val reachable = graph.reachable(from = 1)
    Assertions.assertEquals(expected, reachable)
  }

  @Test
  fun `should return valid topological sort of nodes`() {
    val graph = TestDirectedGraph(
      1 to 2,
      1 to 3,
      2 to 3,
      2 to 4,
      2 to 5,
      5 to 4,
    )
    val expected = listOf(1, 2, 3, 5, 4)
    val sorted = graph.topologicalSort()
    Assertions.assertTrue(sorted is Sorted)
    sorted as Sorted
    Assertions.assertEquals(expected, sorted.sorted)
  }

  @Test
  fun `should return all nodes on topological sort of nodes`() {
    val graph = TestDirectedGraph(
      1 to 2,
      1 to 3,
      2 to 3,
      2 to 4,
      2 to 5,
      5 to 4,
      6 to 7,
      8 to 9,
    )
    val expected = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
    val sorted = graph.topologicalSort()
    Assertions.assertTrue(sorted is Sorted)
    sorted as Sorted
    val returnedFromSort = sorted.sorted.toSet()
    Assertions.assertEquals(expected, returnedFromSort)
  }

  @Test
  fun `should return not visited in topological sort when cycle detected`() {
    val graph = TestDirectedGraph(
      5 to 6,
      1 to 2,
      3 to 3,
      3 to 1,
    )
    val expected = setOf(1, 2, 3)
    val returnedFromSort = graph.topologicalSort()
    Assertions.assertTrue(returnedFromSort is WithCycle)
    returnedFromSort as WithCycle
    Assertions.assertEquals(expected, returnedFromSort.cycle)
  }
}

internal class TestDirectedGraph<V>(vararg edge: Pair<V, V>) : DirectedGraph<V> {
  private val succ = MutableDefaultMap<V, HashSet<V>>(withSet()).also { map ->
    edge.forEach { (from, to) -> map[from] += to }
  }
  private val pred = MutableDefaultMap<V, HashSet<V>>(withSet()).also { map ->
    edge.forEach { (from, to) -> map[to] += from }
  }
  override val nodes: Set<V> get() = succ.keys + pred.keys
  override fun successors(v: V): Set<V> = succ[v]
  override fun predecessors(v: V): Set<V> = pred[v]
}

internal class TestUndirectedGraph<V>(vararg edge: Pair<V, V>) : UndirectedGraph<V>() {
  private val succ = MutableDefaultMap<V, HashSet<V>>(withSet()).also { map ->
    edge.forEach { (from, to) -> map[from] += to }
  }
  private val pred = MutableDefaultMap<V, HashSet<V>>(withSet()).also { map ->
    edge.forEach { (from, to) -> map[to] += from }
  }
  private val conn = MutableDefaultMap<V, HashSet<V>>({ HashSet(succ[it] + pred[it]) })
  override val nodes: Set<V> get() = succ.keys + pred.keys
  override fun connected(v: V): Set<V> = conn[v]
}
