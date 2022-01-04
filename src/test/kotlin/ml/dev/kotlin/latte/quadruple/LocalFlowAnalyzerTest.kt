package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.nlString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LocalFlowAnalyzerTest {

  @Test
  fun `test small example`() = testLocalFlow(
    quadruples = quadrupleDsl {
      v("t") eq v("a")
      v("a") eq v("b")
      v("b") eq v("t")
    },
    aliveBefore = mapOf(
      0 to setOf(v("a"), v("b")),
      1 to setOf(v("b"), v("t")),
      2 to setOf(v("t")),
    ),
    aliveAfter = mapOf(
      0 to setOf(v("t"), v("b")),
      1 to setOf(v("t")),
      2 to setOf(),
    ),
    definedAt = mapOf(
      0 to setOf(v("t")),
      1 to setOf(v("a")),
      2 to setOf(v("b")),
    ),
    usedAt = mapOf(
      0 to setOf(v("a")),
      1 to setOf(v("b")),
      2 to setOf(v("t")),
    ),
  )

  @Test
  fun `test bigger example`() = testLocalFlow(
    quadruples = quadrupleDsl {
      v("t") eq v("a")
      v("a") eq v("b")
      v("b") eq v("t")
      v("t") eq v("a") - v("b")
      v("u") eq v("a") + v("c")
      v("v") eq v("t") * v("u")
      v("d") eq v("v") % v("u")
    },
    aliveBefore = mapOf(
      0 to setOf(v("a"), v("b"), v("c")),
      1 to setOf(v("b"), v("c"), v("t")),
      2 to setOf(v("a"), v("c"), v("t")),
      3 to setOf(v("a"), v("b"), v("c")),
      4 to setOf(v("a"), v("t"), v("c")),
      5 to setOf(v("t"), v("u")),
      6 to setOf(v("v"), v("u")),
    ),
    aliveAfter = mapOf(
      0 to setOf(v("b"), v("c"), v("t")),
      1 to setOf(v("a"), v("c"), v("t")),
      2 to setOf(v("a"), v("b"), v("c")),
      3 to setOf(v("a"), v("t"), v("c")),
      4 to setOf(v("t"), v("u")),
      5 to setOf(v("v"), v("u")),
      6 to setOf(v("d")),
    ),
    definedAt = mapOf(
      0 to setOf(v("t")),
      1 to setOf(v("a")),
      2 to setOf(v("b")),
      3 to setOf(v("t")),
      4 to setOf(v("u")),
      5 to setOf(v("v")),
      6 to setOf(v("d")),
    ),
    usedAt = mapOf(
      0 to setOf(v("a")),
      1 to setOf(v("b")),
      2 to setOf(v("t")),
      3 to setOf(v("a"), v("b")),
      4 to setOf(v("a"), v("c")),
      5 to setOf(v("t"), v("u")),
      6 to setOf(v("v"), v("u")),
    ),
    aliveOut = setOf(v("d"))
  )
}

private fun testLocalFlow(
  quadruples: QuadrupleDSL,
  aliveBefore: Map<Int, Set<VirtualReg>>,
  aliveAfter: Map<Int, Set<VirtualReg>>,
  definedAt: Map<Int, Set<VirtualReg>>,
  usedAt: Map<Int, Set<VirtualReg>>,
  aliveOut: Set<VirtualReg> = emptySet(),
) {

  val stmts = quadruples.statements
  val analysis = LocalFlowAnalyzer.analyze(stmts, aliveOut)

  assertEquals(stmts, analysis.statements)

  analysis.statements.testAnalysis("aliveBefore", aliveBefore, analysis.aliveBefore)
  analysis.statements.testAnalysis("aliveAfter", aliveAfter, analysis.aliveAfter)
  analysis.statements.testAnalysis("definedAt", definedAt, analysis.definedAt)
  analysis.statements.testAnalysis("usedAt", usedAt, analysis.usedAt)
}

internal fun List<Quadruple>.testAnalysis(
  name: String,
  expected: Map<Int, Set<VirtualReg>>,
  result: DefaultMap<Int, Set<VirtualReg>>
): Unit = indices.forEach { idx ->
  assertEquals(expected.getOrElse(idx) { emptySet() }, result[idx]) {
    "Different $name at $idx stmt from ${indices.nlString { "$it:\t${this[it].repr()}" }}"
  }
}
