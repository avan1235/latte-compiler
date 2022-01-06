package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.mangled
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.nlString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class GlobalFlowAnalyzerTest {

  @Nested
  inner class ToLinearTest {
    @Test
    fun `test id function example`() = testLinearGlobalFlow(
      program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        return x;
      }
    """,
      /**
       * 0: f@int(x#0):
       * 1:  ret x#0
       */
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
    fun `test if else function example`() = testLinearGlobalFlow(
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
       * 0:  f@int(x#0):
       * 1:    y@1#0 = 0
       * 2:    if x#0 eq 42 goto L2
       * 3:    y@1#3 = x#0
       * 4:    y@1#2 = y@1#3
       * 5:    goto L4
       * 6:  L2:
       * 7:    @T5#0 = neg x#0
       * 8:    y@1#1 = @T5#0
       * 9:    y@1#2 = y@1#1
       * 10: L4:
       * 11:   ret y@1#2
       */
      label = "f" mangled listOf(IntType),
      aliveBefore = mapOf(
        0 to setOf(),
        1 to setOf(intArg("x#0", 0)),
        2 to setOf(intArg("x#0", 0)),
        3 to setOf(intArg("x#0", 0)),
        4 to setOf(intLoc("y@1#3")),
        5 to setOf(intLoc("y@1#2")),
        6 to setOf(intArg("x#0", 0)),
        7 to setOf(intArg("x#0", 0)),
        8 to setOf(intLoc("@T5#0")),
        9 to setOf(intLoc("y@1#1")),
        10 to setOf(intLoc("y@1#2")),
        11 to setOf(intLoc("y@1#2")),
      ),
      aliveAfter = mapOf(
        0 to setOf(intArg("x#0", 0)),
        1 to setOf(intArg("x#0", 0)),
        2 to setOf(intArg("x#0", 0)),
        3 to setOf(intLoc("y@1#3")),
        4 to setOf(intLoc("y@1#2")),
        5 to setOf(intLoc("y@1#2")),
        6 to setOf(intArg("x#0", 0)),
        7 to setOf(intLoc("@T5#0")),
        8 to setOf(intLoc("y@1#1")),
        9 to setOf(intLoc("y@1#2")),
        10 to setOf(intLoc("y@1#2")),
        11 to setOf(),
      ),
      definedAt = mapOf(
        0 to setOf(intArg("x#0", 0)),
        1 to setOf(intLoc("y@1#0")),
        2 to setOf(),
        3 to setOf(intLoc("y@1#3")),
        4 to setOf(intLoc("y@1#2")),
        5 to setOf(),
        6 to setOf(),
        7 to setOf(intLoc("@T5#0")),
        8 to setOf(intLoc("y@1#1")),
        9 to setOf(intLoc("y@1#2")),
        10 to setOf(),
        11 to setOf(),
      ),
      usedAt = mapOf(
        0 to setOf(),
        1 to setOf(),
        2 to setOf(intArg("x#0", 0)),
        3 to setOf(intArg("x#0", 0)),
        4 to setOf(intLoc("y@1#3")),
        5 to setOf(),
        6 to setOf(),
        7 to setOf(intArg("x#0", 0)),
        8 to setOf(intLoc("@T5#0")),
        9 to setOf(intLoc("y@1#1")),
        10 to setOf(),
        11 to setOf(intLoc("y@1#2")),
      ),
    )

    @Test
    fun `test while function example`() = testLinearGlobalFlow(
      program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        int y = 0;
        while (y < x) {
          y++;
        }
        return y;
      }
    """,
      /**
       * 0:  f@int(x#0):
       * 1:    y@1#0 = 0
       * 2:    y@1#1 = y@1#0
       * 3:    goto L3
       * 4:  L2:
       * 5:    y@1#2 = inc y@1#1
       * 6:    y@1#1 = y@1#2
       * 7:  L3:
       * 8:    if y@1#1 lt x#0 goto L2
       * 9:    ret y@1#1
       */
      label = "f" mangled listOf(IntType),
      aliveBefore = mapOf(
        0 to setOf(),
        1 to setOf(intArg("x#0", 0)),
        2 to setOf(intArg("x#0", 0), intLoc("y@1#0")),
        3 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        4 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        5 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        6 to setOf(intArg("x#0", 0), intLoc("y@1#2")),
        7 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        8 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        9 to setOf(intLoc("y@1#1")),
      ),
      aliveAfter = mapOf(
        0 to setOf(intArg("x#0", 0)),
        1 to setOf(intArg("x#0", 0), intLoc("y@1#0")),
        2 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        3 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        4 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        5 to setOf(intArg("x#0", 0), intLoc("y@1#2")),
        6 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        7 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        8 to setOf(intLoc("y@1#1"), intArg("x#0", 0)),
        9 to setOf(),
      ),
    )
  }

  @Nested
  inner class ToGraphTest {
    @Test
    fun `test id function example`() = testGraphGlobalFlow(
      program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        return x;
      }
    """,
      /**
       * 0: f@int(x#0):
       * 1:  ret x#0
       */
      /**
       * 0: f@int(x#0):
       * 1:  ret x#0
       */
      label = "f" mangled listOf(IntType),
      aliveBefore = mapOf(
        "f@int".label to mapOf(
          0 to setOf(),
          1 to setOf(intArg("x#0", 0))
        ),
      ),
      aliveAfter = mapOf(
        "f@int".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(),
        )
      ),
      definedAt = mapOf(
        "f@int".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(),
        )
      ),
      usedAt = mapOf(
        "f@int".label to mapOf(
          0 to setOf(),
          1 to setOf(intArg("x#0", 0)),
        )
      ),
    )

    @Test
    fun `test if else function example`() = testGraphGlobalFlow(
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
       * 0:  f@int(x#0):
       * 1:    y@1#0 = 0
       * 2:    if x#0 eq 42 goto L2
       * 3:  G6:
       * 4:    goto L3
       * 5:  L3:
       * 6:    y@1#3 = x#0
       * 7:    y@1#2 = y@1#3
       * 8:    goto L4
       * 9:  L2:
       * 10:   @T5#0 = neg x#0
       * 11:   y@1#1 = @T5#0
       * 12:   y@1#2 = y@1#1
       * 13: L4:
       * 14:   ret y@1#2
       */
      label = "f" mangled listOf(IntType),
      aliveBefore = mapOf(
        "f@int".label to mapOf(
          0 to setOf(),
          1 to setOf(intArg("x#0", 0)),
          2 to setOf(intArg("x#0", 0)),
        ),
        "G6".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0)),
        ),
        "L3".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0)),
          2 to setOf(intLoc("y@1#3")),
          3 to setOf(intLoc("y@1#2")),
        ),
        "L2".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0)),
          2 to setOf(intLoc("@T5#0")),
          3 to setOf(intLoc("y@1#1")),
        ),
        "L4".label to mapOf(
          0 to setOf(intLoc("y@1#2")),
          1 to setOf(intLoc("y@1#2")),
        )
      ),
      aliveAfter = mapOf(
        "f@int".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0)),
          2 to setOf(intArg("x#0", 0)),
        ),
        "G6".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0)),
        ),
        "L3".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intLoc("y@1#3")),
          2 to setOf(intLoc("y@1#2")),
          3 to setOf(intLoc("y@1#2")),
        ),
        "L2".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intLoc("@T5#0")),
          2 to setOf(intLoc("y@1#1")),
          3 to setOf(intLoc("y@1#2")),
          4 to setOf(intLoc("y@1#2")),
          5 to setOf(),
        ),
        "L4".label to mapOf(
          0 to setOf(intLoc("y@1#2")),
          1 to setOf(),
        ),
      ),
    )

    @Test
    fun `test while function example`() = testGraphGlobalFlow(
      program = """
      int main() {
        return f(42);
      }
      int f(int x) {
        int y = 0;
        while (y < x) {
          y++;
        }
        return y;
      }
    """,
      /**
        * 0:  f@int(x#0):
        * 1:    y@1#0 = 0
        * 2:    y@1#1 = y@1#0
        * 3:    goto L3
        * 4:  L2:
        * 5:    y@1#2 = inc y@1#1
        * 6:    y@1#1 = y@1#2
        * 7:  L3:
        * 8:    if y@1#1 lt x#0 goto L2
        * 9:  G5:
        * 10:   goto L4
        * 11: L4:
        * 12:   ret y@1#1
       */
      label = "f" mangled listOf(IntType),
      aliveBefore = mapOf(
        "f@int".label to mapOf(
          0 to setOf(),
          1 to setOf(intArg("x#0", 0)),
          2 to setOf(intArg("x#0", 0), intLoc("y@1#0")),
          3 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        ),
        "L2".label to mapOf(
          0 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          1 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          2 to setOf(intArg("x#0", 0), intLoc("y@1#2")),
        ),
        "L3".label to mapOf(
          0 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          1 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        ),
        "G5".label to mapOf(
          0 to setOf(intLoc("y@1#1")),
          1 to setOf(intLoc("y@1#1")),
        ),
        "L4".label to mapOf(
          0 to setOf(intLoc("y@1#1")),
          1 to setOf(intLoc("y@1#1")),
        ),
      ),
      aliveAfter = mapOf(
        "f@int".label to mapOf(
          0 to setOf(intArg("x#0", 0)),
          1 to setOf(intArg("x#0", 0), intLoc("y@1#0")),
          2 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          3 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        ),
        "L2".label to mapOf(
          0 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          1 to setOf(intArg("x#0", 0), intLoc("y@1#2")),
          2 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
        ),
        "L3".label to mapOf(
          0 to setOf(intArg("x#0", 0), intLoc("y@1#1")),
          1 to setOf(intLoc("y@1#1"), intArg("x#0", 0)),
        ),
        "G5".label to mapOf(
          0 to setOf(intLoc("y@1#1")),
          1 to setOf(intLoc("y@1#1")),
        ),
        "L4".label to mapOf(
          0 to setOf(intLoc("y@1#1")),
          1 to setOf(),
        ),
      ),
    )
  }
}

private fun testLinearGlobalFlow(
  program: String,
  label: String,
  aliveBefore: Map<Int, Set<VirtualReg>>? = null,
  aliveAfter: Map<Int, Set<VirtualReg>>? = null,
  definedAt: Map<Int, Set<VirtualReg>>? = null,
  usedAt: Map<Int, Set<VirtualReg>>? = null,
) {
  val (graph, _) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
    transformFromSSA()
  }
  val cfg = graph.functions[label.label] ?: fail { "Not found function for label $label" }
  val linearAnalysis = GlobalFlowAnalyzer.analyzeToLinear(cfg).peepHoleOptimize()
  assertTrue(linearAnalysis.statements.isNotEmpty())
  aliveBefore?.let { linearAnalysis.statements.testAnalysis("aliveBefore", it, linearAnalysis.aliveBefore) }
  aliveAfter?.let { linearAnalysis.statements.testAnalysis("aliveAfter", it, linearAnalysis.aliveAfter) }
  definedAt?.let { linearAnalysis.statements.testAnalysis("definedAt", it, linearAnalysis.definedAt) }
  usedAt?.let { linearAnalysis.statements.testAnalysis("usedAt", it, linearAnalysis.usedAt) }
}

private fun testGraphGlobalFlow(
  program: String,
  label: String,
  aliveBefore: Map<Label, Map<Int, Set<VirtualReg>>>? = null,
  aliveAfter: Map<Label, Map<Int, Set<VirtualReg>>>? = null,
  definedAt: Map<Label, Map<Int, Set<VirtualReg>>>? = null,
  usedAt: Map<Label, Map<Int, Set<VirtualReg>>>? = null,
) {
  val (graph, _) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
    transformFromSSA()
  }
  val cfg = graph.functions[label.label] ?: fail { "Not found function for label $label" }
  val graphAnalysis = GlobalFlowAnalyzer.analyzeToGraph(cfg)
  aliveBefore?.let { graphAnalysis.graph.testAnalysis("aliveBefore", it, graphAnalysis.aliveBefore) }
  aliveAfter?.let { graphAnalysis.graph.testAnalysis("aliveAfter", it, graphAnalysis.aliveAfter) }
  definedAt?.let { graphAnalysis.graph.testAnalysis("definedAt", it, graphAnalysis.definedAt) }
  usedAt?.let { graphAnalysis.graph.testAnalysis("usedAt", it, graphAnalysis.usedAt) }
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

internal fun FunctionControlFlowGraph.testAnalysis(
  name: String,
  expected: Map<Label, Map<Int, Set<VirtualReg>>>,
  result: DefaultMap<GraphLocation, Set<VirtualReg>>
): Unit = block.values.forEach { block ->
  val statements = block.statements.toList()
  statements.indices.forEach { idx ->
    val e = expected[block.label]?.get(idx) ?: emptySet()
    val r = result[GraphLocation(block.label, idx)]
    Assertions.assertEquals(e, r) {
      "Different $name at $idx stmt of label ${block.label.name} from " +
        statements.indices.nlString { "$it:\t${statements[it].repr()}" }
    }
  }
}
