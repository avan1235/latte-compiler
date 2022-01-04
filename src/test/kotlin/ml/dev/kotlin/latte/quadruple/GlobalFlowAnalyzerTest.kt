package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.mangled
import ml.dev.kotlin.latte.typecheck.typeCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class GlobalFlowAnalyzerTest {

  @Test
  fun `test id function example`() = testGlobalFlow(
    program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        return x;
      }
    """,
    label = "f" mangled listOf(IntType),
    aliveBefore = mapOf(
      0 to setOf(),
      1 to setOf(intArg("x#0", 0)),
    ),
    aliveAfter = mapOf(
      0 to setOf(intArg("x#0", 0)),
      1 to setOf(),
    ),
    definedAt = mapOf(
      0 to setOf(intArg("x#0", 0)),
      1 to setOf(),
    ),
    usedAt = mapOf(
      0 to setOf(),
      1 to setOf(intArg("x#0", 0)),
    ),
  )

  @Test
  fun `test if else function example`() = testGlobalFlow(
    program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        int y = 0;
        if (x == 42) {
          y = -x;
        }
        else {
          y = x;
        }
        return y;
      }
    """,
    /**
     * 0: 	f@int(x#0):
     * 1: 	  y@1#0 = 0
     * 2: 	  if x#0 eq 42 goto @L2
     * 3: 	@G6:
     * 4: 	  goto @L3
     * 5: 	@L3:
     * 6: 	  y@1#3 = x#0
     * 7: 	  y@1#2 = y@1#3
     * 8: 	  goto @L4
     * 9: 	@L2:
     * 10:	  @T5#0 = neg x#0
     * 11:	  y@1#1 = @T5#0
     * 12:	  y@1#2 = y@1#1
     * 13:	@L4:
     * 14:	  ret y@1#2
     */
    label = "f" mangled listOf(IntType),
    aliveBefore = mapOf(
      0 to setOf(),
      1 to setOf(intArg("x#0", 0)),
      2 to setOf(intArg("x#0", 0)),
      3 to setOf(intArg("x#0", 0)),
      4 to setOf(intArg("x#0", 0)),
      5 to setOf(intArg("x#0", 0)),
      6 to setOf(intArg("x#0", 0)),
      7 to setOf(intLoc("y@1#3")),
      8 to setOf(intLoc("y@1#2")),
      9 to setOf(intArg("x#0", 0)),
      10 to setOf(intArg("x#0", 0)),
      11 to setOf(intLoc("@T5#0")),
      12 to setOf(intLoc("y@1#1")),
      13 to setOf(intLoc("y@1#2")),
      14 to setOf(intLoc("y@1#2")),
    ),
    aliveAfter = mapOf(
      0 to setOf(intArg("x#0", 0)),
      1 to setOf(intArg("x#0", 0)),
      2 to setOf(intArg("x#0", 0)),
      3 to setOf(intArg("x#0", 0)),
      4 to setOf(intArg("x#0", 0)),
      5 to setOf(intArg("x#0", 0)),
      6 to setOf(intLoc("y@1#3")),
      7 to setOf(intLoc("y@1#2")),
      8 to setOf(intLoc("y@1#2")),
      9 to setOf(intArg("x#0", 0)),
      10 to setOf(intLoc("@T5#0")),
      11 to setOf(intLoc("y@1#1")),
      12 to setOf(intLoc("y@1#2")),
      13 to setOf(intLoc("y@1#2")),
      14 to setOf(),
    ),
    definedAt = mapOf(
      0 to setOf(intArg("x#0", 0)),
      1 to setOf(intLoc("y@1#0")),
      2 to setOf(),
      3 to setOf(),
      4 to setOf(),
      5 to setOf(),
      6 to setOf(intLoc("y@1#3")),
      7 to setOf(intLoc("y@1#2")),
      8 to setOf(),
      9 to setOf(),
      10 to setOf(intLoc("@T5#0")),
      11 to setOf(intLoc("y@1#1")),
      12 to setOf(intLoc("y@1#2")),
      13 to setOf(),
      14 to setOf(),
    ),
    usedAt = mapOf(
      0 to setOf(),
      1 to setOf(),
      2 to setOf(intArg("x#0", 0)),
      3 to setOf(),
      4 to setOf(),
      5 to setOf(),
      6 to setOf(intArg("x#0", 0)),
      7 to setOf(intLoc("y@1#3")),
      8 to setOf(),
      9 to setOf(),
      10 to setOf(intArg("x#0", 0)),
      11 to setOf(intLoc("@T5#0")),
      12 to setOf(intLoc("y@1#1")),
      13 to setOf(),
      14 to setOf(intLoc("y@1#2")),
    ),
  )
}

private fun testGlobalFlow(
  program: String,
  label: String,
  aliveBefore: Map<Int, Set<VirtualReg>>,
  aliveAfter: Map<Int, Set<VirtualReg>>,
  definedAt: Map<Int, Set<VirtualReg>>,
  usedAt: Map<Int, Set<VirtualReg>>,
) {
  val (graph, _) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
    transformFromSSA()
  }
  val cfg = graph.functions[label.label] ?: fail { "Not found function for label $label" }
  val analysis = GlobalFlowAnalyzer.analyze(cfg)

  assertEquals(cfg.orderedBlocks().sumOf { it.statements.count() }, analysis.statements.size)

  analysis.statements.testAnalysis("aliveBefore", aliveBefore, analysis.aliveBefore)
  analysis.statements.testAnalysis("aliveAfter", aliveAfter, analysis.aliveAfter)
  analysis.statements.testAnalysis("definedAt", definedAt, analysis.definedAt)
  analysis.statements.testAnalysis("usedAt", usedAt, analysis.usedAt)
}

private fun intLoc(reg: String): LocalValue =
  LocalValue(
    reg, IntType,
    if (reg.contains("#")) LocalValue(reg.takeWhile { it != '#' }, IntType, null) else null
  )

private fun intArg(reg: String, idx: Int): ArgValue =
  ArgValue(
    reg, idx, IntType,
    if (reg.contains("#")) ArgValue(reg.takeWhile { it != '#' }, idx, IntType, null) else null
  )
