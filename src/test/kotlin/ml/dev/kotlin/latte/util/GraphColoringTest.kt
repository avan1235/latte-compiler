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
    val extraColor = D
    val graph = TestUndirectedGraph(
      '1' to '2',
      '1' to '3',
      '1' to '4',
      '2' to '3',
      '2' to '4',
      '2' to '5',
      '2' to '6',
      '3' to '4',
      '3' to '5',
      '5' to '6',
    )
    val graphColoring = GraphColoring(
      colors = setOf(A, B, C),
      extraColor,
      graph,
      spillSelectHeuristics = { it.firstEntry().value.first() },
      colorSelectHeuristics = { it.first() }
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test coloring with last heuristics on sample small graph`() {
    val extraColor = D
    val graph = TestUndirectedGraph(
      '1' to '2',
      '1' to '3',
      '1' to '4',
      '2' to '3',
      '2' to '4',
      '2' to '5',
      '2' to '6',
      '3' to '4',
      '3' to '5',
      '5' to '6',
    )
    val graphColoring = GraphColoring(
      colors = setOf(A, B, C),
      extraColor,
      graph,
      spillSelectHeuristics = { it.firstEntry().value.last() },
      colorSelectHeuristics = { it.last() }
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test repeated graph with no extra colors needed in ideal coloring`() {
    val extraColor = D
    val graph = TestUndirectedGraph(
      *(0..5).flatMap { (it + 1..it + 2).map { n -> "$it" to "${n % 6}" } }.toTypedArray()
    )
    val graphColoring = GraphColoring(
      colors = setOf(A, B, C),
      extraColor,
      graph,
      spillSelectHeuristics = { it.firstEntry().value.last() },
      colorSelectHeuristics = { it.last() }
    )
    graphColoring.assertValidOn(graph, extraColor)
  }

  @Test
  fun `test on dense graph with random heuristics values`() {
    val extraColor = I
    val graph = TestUndirectedGraph(
      *(0..511).flatMap { v -> (0..128).map { "$v" to "${(0..511).random()}" } }
        .filter { it.first != it.second }.toTypedArray()
    )
    val graphColoring = GraphColoring(
      colors = setOf(A, B, C, D, E, F, G, H),
      extraColor,
      graph,
      spillSelectHeuristics = { it.firstEntry().value.toList().random() },
      colorSelectHeuristics = { it.random() }
    )
    graphColoring.assertValidOn(graph, extraColor)
  }


  @Test
  fun `test throws on graph with self loops`() {
    val extraColor = D
    val graph = TestUndirectedGraph(
      "1" to "1",
      "1" to "2"
    )
    assertThrows<IllegalArgumentException> {
      GraphColoring(
        colors = setOf(A, B, C),
        extraColor,
        graph,
        spillSelectHeuristics = { it.firstEntry().value.toList().random() },
        colorSelectHeuristics = { it.random() }
      )
    }
  }
}

private fun <N, C> GraphColoring<N, C>.assertValidOn(graph: UndirectedGraph<N>, extraColor: C) {
  assertTrue(coloring.values.toSet() != setOf(extraColor), "Only extra color in coloring present")
  assertEquals(graph.nodes, coloring.keys)
  graph.nodes.firstOrNull { coloring[it] == null }?.let { fail("Not defined color in graph for node $it") }
  graph.nodes.forEach { node ->
    val neighColors = graph.connected(node).mapTo(HashSet()) { coloring[it]!! }
    val color = coloring[node]!!
    assertTrue(color == extraColor || color !in neighColors) { "Invalid color $color for $node in $this" }
  }
}

