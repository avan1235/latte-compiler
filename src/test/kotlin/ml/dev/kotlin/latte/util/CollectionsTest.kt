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
