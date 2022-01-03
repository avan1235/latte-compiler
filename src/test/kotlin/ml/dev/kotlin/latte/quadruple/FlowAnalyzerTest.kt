package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FlowAnalyzerTest {

  @Test
  fun `test small example`() = testFlow(
    quadruples = quadrupleDsl {
      v("t") eq v("a")
      v("a") eq v("b")
      v("b") eq v("t")
    },
    aliveBefore = listOf(
      setOf(v("a"), v("b")),
      setOf(v("b"), v("t")),
      setOf(v("t")),
    ),
    aliveAfter = listOf(
      setOf(v("t"), v("b")),
      setOf(v("t")),
      setOf(),
    ),
    definedAt = listOf(
      setOf(v("t")),
      setOf(v("a")),
      setOf(v("b")),
    ),
    usedAt = listOf(
      setOf(v("a")),
      setOf(v("b")),
      setOf(v("t")),
    ),
  )

  @Test
  fun `test bigger example`() = testFlow(
    quadruples = quadrupleDsl {
      v("t") eq v("a")
      v("a") eq v("b")
      v("b") eq v("t")
      v("t") eq v("a") - v("b")
      v("u") eq v("a") + v("c")
      v("v") eq v("t") * v("u")
      v("d") eq v("v") % v("u")
    },
    aliveBefore = listOf(
      setOf(v("a"), v("b"), v("c")),
      setOf(v("b"), v("c"), v("t")),
      setOf(v("a"), v("c"), v("t")),
      setOf(v("a"), v("b"), v("c")),
      setOf(v("a"), v("t"), v("c")),
      setOf(v("t"), v("u")),
      setOf(v("v"), v("u")),
    ),
    aliveAfter = listOf(
      setOf(v("b"), v("c"), v("t")),
      setOf(v("a"), v("c"), v("t")),
      setOf(v("a"), v("b"), v("c")),
      setOf(v("a"), v("t"), v("c")),
      setOf(v("t"), v("u")),
      setOf(v("v"), v("u")),
      setOf(),
    ),
    definedAt = listOf(
      setOf(v("t")),
      setOf(v("a")),
      setOf(v("b")),
      setOf(v("t")),
      setOf(v("u")),
      setOf(v("v")),
      setOf(v("d")),
    ),
    usedAt = listOf(
      setOf(v("a")),
      setOf(v("b")),
      setOf(v("t")),
      setOf(v("a"), v("b")),
      setOf(v("a"), v("c")),
      setOf(v("t"), v("u")),
      setOf(v("v"), v("u")),
    ),
  )
}

private fun testFlow(
  quadruples: QuadrupleDSL,
  aliveBefore: List<Set<VirtualReg>>,
  aliveAfter: List<Set<VirtualReg>>,
  definedAt: List<Set<VirtualReg>>,
  usedAt: List<Set<VirtualReg>>,
) {

  val stmts = quadruples.statements.toCollection(ArrayDeque())
  val analysis = FlowAnalyzer.analyze(stmts)
  fun test(name: String, expected: List<Set<VirtualReg>>, result: DefaultMap<Int, Set<VirtualReg>>): Unit =
    stmts.indices.forEach { assertEquals(expected.getOrElse(it) { emptySet() }, result[it], "Different $name") }
  test("aliveAfter", aliveAfter, analysis.aliveAfter)
  test("aliveBefore", aliveBefore, analysis.aliveBefore)
  test("definedAt", definedAt, analysis.definedAt)
  test("usedAt", usedAt, analysis.usedAt)
}
