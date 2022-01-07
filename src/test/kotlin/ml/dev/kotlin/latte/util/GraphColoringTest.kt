package ml.dev.kotlin.latte.util

import ml.dev.kotlin.latte.util.GraphColoringTest.Colors.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

internal class GraphColoringTest {

  internal enum class Colors { A, B, C, D, E, F, G, H, I }

  @Test
  fun `test coloring with first heuristics on sample small graph`() {
    val extraColor = { _: String -> D }
    val graph = TestUndirectedGraph(
      "1" to "2",
      "1" to "3",
      "1" to "4",
      "2" to "3",
      "2" to "4",
      "2" to "5",
      "2" to "6",
      "3" to "4",
      "3" to "5",
      "5" to "6",
    )
    val graphColoring = GraphColoring(
      graph,
      colors = setOf(A, B, C),
      strategy = graphColoringStrategy(
        extraColor,
        spillSelectHeuristics = { it.firstEntry().value.first() },
        colorSelectHeuristics = { _, available, _ -> available.first() }
      )
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test coloring with last heuristics on sample small graph`() {
    val extraColor = { _: String -> D }
    val graph = TestUndirectedGraph(
      "1" to "2",
      "1" to "3",
      "1" to "4",
      "2" to "3",
      "2" to "4",
      "2" to "5",
      "2" to "6",
      "3" to "4",
      "3" to "5",
      "5" to "6",
    )
    val graphColoring = GraphColoring(
      graph,
      colors = setOf(A, B, C),
      strategy = graphColoringStrategy(
        extraColor,
        spillSelectHeuristics = { it.firstEntry().value.last() },
        colorSelectHeuristics = { _, available, _ -> available.last() }
      )
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test repeated graph with no extra colors needed in perfect coloring`() {
    val extraColor = { _: String -> D }
    val graph = TestUndirectedGraph(
      *(0..5).flatMap { (it + 1..it + 2).map { n -> "$it" to "${n % 6}" } }.toTypedArray()
    )
    val graphColoring = GraphColoring(
      graph,
      colors = setOf(A, B, C),
      strategy = graphColoringStrategy(
        extraColor,
        spillSelectHeuristics = { it.firstEntry().value.last() },
        colorSelectHeuristics = { _, available, _ -> available.last() }
      )
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test on dense graph with random heuristics values`() {
    val extraColor = { _: String -> I }
    val graph = TestUndirectedGraph(
      *(0..511).flatMap { v -> (0..128).map { "$v" to "${(0..511).random()}" } }
        .filter { it.first != it.second }.toTypedArray()
    )
    val graphColoring = GraphColoring(
      graph,
      colors = setOf(A, B, C, D, E, F, G, H),
      strategy = graphColoringStrategy(
        extraColor,
        spillSelectHeuristics = { it.firstEntry().value.toList().random() },
        colorSelectHeuristics = { _, available, _ -> available.random() }
      )
    )
    graphColoring.assertValidOn(graph, extraColor)
  }


  @Test
  fun `test throws on graph with self loops`() {
    val extraColor = { _: String -> D }
    val graph = TestUndirectedGraph(
      "1" to "1",
      "1" to "2"
    )
    assertThrows<LatteIllegalStateException> {
      GraphColoring(
        graph,
        colors = setOf(A, B, C),
        strategy = graphColoringStrategy(
          extraColor,
          spillSelectHeuristics = { it.firstEntry().value.toList().random() },
          colorSelectHeuristics = { _, available, _ -> available.random() }
        )
      )
    }
  }
}

private fun <N, C> GraphColoring<N, C, C, C>.assertValidOn(graph: UndirectedGraph<N>, extraColor: (N) -> C) {
  assertTrue(coloring.values.toSet() != graph.nodes.map(extraColor).toSet(), "Only extra color in coloring present")
  assertEquals(graph.nodes, coloring.keys)
  graph.nodes.firstOrNull { coloring[it] == null }?.let { fail("Not defined color in graph for node $it") }
  graph.nodes.forEach { node ->
    val neighColors = graph.connected(node).mapTo(HashSet()) { coloring[it]!! }
    val color = coloring[node]!!
    assertTrue(color == extraColor(node) || color !in neighColors) { "Invalid color $color for $node in $this" }
  }
}

