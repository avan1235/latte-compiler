package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class CollectionsTest {

  @Nested
  inner class SplitAtTest {
    @Test
    fun `should split on last element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2), listOf(3)),
      iterable = listOf(1, 2, 3),
      last = { it == 2 },
    )

    @Test
    fun `should split on first element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2), listOf(1, 3)),
      iterable = listOf(1, 2, 1, 3),
      first = { it == 1 },
    )

    @Test
    fun `should split on first and last element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2, 2, 3), listOf(1, 4, 4, 3), listOf(1), listOf(1, 3), listOf(3)),
      iterable = listOf(1, 2, 2, 3, 1, 4, 4, 3, 1, 1, 3, 3),
      first = { it == 1 },
      last = { it == 3 },
    )

    @Test
    fun `should split on first and last single element`() = testSplitAtLast(
      expected = listOf(listOf(1), listOf(1), listOf(1), listOf(1)),
      iterable = listOf(1, 1, 1, 1),
      first = { it == 1 },
      last = { it == 1 },
    )
  }

  @Nested
  inner class GraphTest {
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
      assertEquals(expected, reachable)
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
      assertEquals(expected, sorted)
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
      val returnedFromSort = graph.topologicalSort()?.toSet()
      assertEquals(expected, returnedFromSort)
    }

    @Test
    fun `should return null in topological sort when cycle detected`() {
      val graph = TestDirectedGraph(
        1 to 2,
        1 to 3,
        2 to 3,
        2 to 4,
        2 to 5,
        5 to 4,
        4 to 1,
      )
      val expected = null
      val returnedFromSort = graph.topologicalSort()
      assertEquals(expected, returnedFromSort)
    }
  }
}

private fun <T> testSplitAtLast(
  expected: List<List<T>>,
  iterable: Iterable<T>,
  first: (T) -> Boolean = { false },
  last: (T) -> Boolean = { false },
): Unit = assertEquals(expected, iterable.splitAt(first, last).toList())

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
