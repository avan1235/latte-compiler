package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OptimizeTest {
  @Test
  fun `optimizes add common operations`() = testOptimize(
    program = """
    int main() {
      int a = 42;
      int b = 24;
      int c = a + b;
      int d = b + a;
      int e = b * a;
      int f = a * b;
      int g = c - d;
      int h = e / f;
      printInt(g);
      printInt(h);
      return 0;
    }
    """,
    irRepresentation = """
    main():
      a@0#0 = 42
      b@1#0 = 24
      @T2#0 = a@0#0 plus b@1#0
      c@3#0 = @T2#0
      @T4#0 = b@1#0 plus a@0#0
      d@5#0 = @T4#0
      @T6#0 = b@1#0 times a@0#0
      e@7#0 = @T6#0
      @T8#0 = a@0#0 times b@1#0
      f@9#0 = @T8#0
      @T10#0 = c@3#0 minus d@5#0
      g@11#0 = @T10#0
      @T12#0 = e@7#0 divide f@9#0
      h@13#0 = @T12#0
      @T14#0 = call __printInt (g@11#0)
      @T15#0 = call __printInt (h@13#0)
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      a@0#0 = 42
      b@1#0 = 24
      c@3#0 = a@0#0 plus b@1#0
      d@5#0 = c@3#0
      e@7#0 = b@1#0 times a@0#0
      f@9#0 = e@7#0
      g@11#0 = c@3#0 minus d@5#0
      h@13#0 = e@7#0 divide f@9#0
      @T14#0 = call __printInt (g@11#0)
      @T15#0 = call __printInt (h@13#0)
      ret 0
    """
  )
}

private fun testOptimize(
  program: String,
  irRepresentation: String,
  optimizedIrRepresentation: String,
) {
  val (graph, str, vTable) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
  }
  val instructions = graph.instructions().peepHoleOptimize(extract = { it }).asIterable()
  val lcseInstructions = graph.apply { optimize() }.instructions().peepHoleOptimize(extract = { it }).asIterable()
  val repr = instructions.nlString { it.repr() }
  val lcseRepr = lcseInstructions.nlString { it.repr() }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr, "Invalid IR Representation")
  assertEquals("\n${optimizedIrRepresentation.trimIndent()}\n", lcseRepr, "Invalid optimized IR Representation")
  assert(instructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
  assert(lcseInstructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
}
