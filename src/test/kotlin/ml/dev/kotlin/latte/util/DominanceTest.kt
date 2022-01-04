package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DominanceTest {

  @Test
  fun `works for article sample 1`() = testDominators(
    root = 6,
    graph = TestDirectedGraph(
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
    1 to (6 withFrontiers setOf(2)),
    2 to (6 withFrontiers setOf(1, 3)),
    3 to (6 withFrontiers setOf(2)),
    4 to (6 withFrontiers setOf(2, 3)),
    5 to (6 withFrontiers setOf(1)),
    6 to (6 withFrontiers setOf()),
  )

  @Test
  fun `works for article sample 2`() = testDominators(
    root = 5,
    graph = TestDirectedGraph(
      5 to 4,
      5 to 3,
      4 to 1,
      3 to 2,
      1 to 2,
      2 to 1,
    ),
    1 to (5 withFrontiers setOf(2)),
    2 to (5 withFrontiers setOf(1)),
    3 to (5 withFrontiers setOf(2)),
    4 to (5 withFrontiers setOf(1)),
    5 to (5 withFrontiers setOf()),
  )

  @Test
  fun `works for wikipedia dominator sample`() = testDominators(
    root = 1,
    graph = TestDirectedGraph(
      1 to 2,
      2 to 6,
      2 to 4,
      4 to 5,
      5 to 2,
      2 to 3,
      3 to 5,
    ),
    1 to (1 withFrontiers setOf()),
    2 to (1 withFrontiers setOf(2)),
    3 to (2 withFrontiers setOf(5)),
    4 to (2 withFrontiers setOf(5)),
    5 to (2 withFrontiers setOf(2)),
    6 to (2 withFrontiers setOf()),
  )

  @Test
  fun `works for line graph`() = testDominators(
    root = 1,
    graph = TestDirectedGraph(
      1 to 2,
      2 to 3,
      3 to 4,
      4 to 5,
    ),
    1 to (1 withFrontiers setOf()),
    2 to (1 withFrontiers setOf()),
    3 to (2 withFrontiers setOf()),
    4 to (3 withFrontiers setOf()),
    5 to (4 withFrontiers setOf()),
  )

  @Test
  fun `works for bigger graph`() = testDominators(
    root = 1,
    graph = TestDirectedGraph(
      1 to 2,
      1 to 5,
      1 to 9,
      5 to 6,
      5 to 7,
      6 to 8,
      7 to 8,
      8 to 5,
      6 to 4,
      7 to 12,
      4 to 13,
      8 to 13,
      12 to 13,
      2 to 3,
      3 to 3,
      3 to 4,
      9 to 10,
      9 to 11,
      11 to 12,
      10 to 12,
    ),
    1 to (1 withFrontiers setOf()),
    2 to (1 withFrontiers setOf(4)),
    3 to (2 withFrontiers setOf(3, 4)),
    4 to (1 withFrontiers setOf(13)),
    5 to (1 withFrontiers setOf(4, 5, 12, 13)),
    6 to (5 withFrontiers setOf(4, 8)),
    7 to (5 withFrontiers setOf(8, 12)),
    8 to (5 withFrontiers setOf(5, 13)),
    9 to (1 withFrontiers setOf(12)),
    10 to (9 withFrontiers setOf(12)),
    11 to (9 withFrontiers setOf(12)),
    12 to (1 withFrontiers setOf(13)),
    13 to (1 withFrontiers setOf()),
  )
}

private fun testDominators(root: Int, graph: DirectedGraph<Int>, vararg dominators: Pair<Int, Pair<Int, Set<Int>>>) {
  val expectedDominator = dominators.toMap().mapValues { it.value.first }
  val expectedDominanceFrontiers = dominators.toMap().mapValues { it.value.second }
  val dom = Dominance(root, graph)
  val dominator = graph.nodes.associateWith { dom.dominator(it) }
  val dominateFrontiers = graph.nodes.associateWith { dom.frontiers(it) }
  assertEquals(expectedDominator, dominator, "Dominator")
  assertEquals(expectedDominanceFrontiers, dominateFrontiers, "Dominate frontiers")
}

private infix fun Int.withFrontiers(s: Set<Int>): Pair<Int, Set<Int>> = this to s
