package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DominanceTest {

  @Test
  fun `works for article sample`() = testDominators(
    root = 6,
    graph = TestGraph(
      6 to 5,
      6 to 4,
      5 to 1,
      4 to 2,
      4 to 3,
      1 to 2,
      2 to 1,
      2 to 3,
      3 to 2,
    ),
    6 to 6,
    5 to 6,
    4 to 6,
    3 to 6,
    2 to 6,
    1 to 6,
  )
}

private fun testDominators(root: Int, graph: Graph<Int>, vararg dominators: Pair<Int, Int>) {
  val expected = dominators.toMap()
  val dom = Dominance(root, graph)
  val associated = graph.nodes.associateWith { dom.dominator(it) }
  assertEquals(expected, associated)
}

private class TestGraph(vararg edge: Pair<Int, Int>) : Graph<Int> {

  private val succ = MutableDefaultMap<Int, LinkedHashSet<Int>>({ LinkedHashSet() }).also { map ->
    edge.forEach { (from, to) -> map[from] += to }
  }
  private val pred = MutableDefaultMap<Int, LinkedHashSet<Int>>({ LinkedHashSet() }).also { map ->
    edge.forEach { (from, to) -> map[to] += from }
  }
  override val size: Int = succ.keys.size
  override val nodes: Set<Int> get() = succ.keys + pred.keys
  override fun successors(v: Int): LinkedHashSet<Int> = succ[v]
  override fun predecessors(v: Int): LinkedHashSet<Int> = pred[v]
}
