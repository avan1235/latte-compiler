package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DominanceTest {

  @Test
  fun `works for article sample 1`() = testDominators(
    root = 6,
    graph = TestGraph(
      6 to 5,
      6 to 4,
      4 to 2,
      4 to 3,
      2 to 1,
      2 to 3,
      3 to 2,
      1 to 2,
      5 to 1,
    ),
    6 to 6,
    5 to 6,
    4 to 6,
    3 to 6,
    2 to 6,
    1 to 6,
  )

  @Test
  fun `works for article sample 2`() = testDominators(
    root = 5,
    graph = TestGraph(
      5 to 4,
      5 to 3,
      4 to 1,
      3 to 2,
      1 to 2,
      2 to 1,
    ),
1 to 5,
    2 to 5,
    3 to 5,
    4 to 5,
    5 to 5,
  )


  @Test
  fun `works for wikipedia dominator sample`() = testDominators(
    root = 1,
    graph = TestGraph(
      1 to 2,
      2 to 6,
      2 to 4,
      4 to 5,
      5 to 2,
      2 to 3,
      3 to 5,
    ),
    1 to 1,
    2 to 1,
    3 to 2,
    4 to 2,
    5 to 2,
    6 to 2,
  )
}

private fun testDominators(root: Int, graph: Graph<Int>, vararg dominators: Pair<Int, Int>) {
  val expected = dominators.toMap()
  val dom = Dominance(root, graph)
  val associated = graph.nodes.associateWith { dom.dominator(it) }
  graph.nodes.forEach { println("DF $it = ${dom.dominanceFrontier(it)}") }
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
  override fun toString(): String = "TestGraph(succ=$succ, size=$size)"
}
