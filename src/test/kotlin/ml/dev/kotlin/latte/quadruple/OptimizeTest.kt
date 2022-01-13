package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OptimizeTest {
  @Test
  fun `optimizes common operations`() = testOptimize(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
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
      @T0#0 = call __readInt ()
      a@1#0 = @T0#0
      @T2#0 = call __readInt ()
      b@3#0 = @T2#0
      @T4#0 = a@1#0 plus b@3#0
      c@5#0 = @T4#0
      @T6#0 = b@3#0 plus a@1#0
      d@7#0 = @T6#0
      @T8#0 = b@3#0 times a@1#0
      e@9#0 = @T8#0
      @T10#0 = a@1#0 times b@3#0
      f@11#0 = @T10#0
      @T12#0 = c@5#0 minus d@7#0
      g@13#0 = @T12#0
      @T14#0 = e@9#0 divide f@11#0
      h@15#0 = @T14#0
      @T16#0 = call __printInt (g@13#0)
      @T17#0 = call __printInt (h@15#0)
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      a@1#0 = call __readInt ()
      b@3#0 = call __readInt ()
      c@5#0 = a@1#0 plus b@3#0
      d@7#0 = c@5#0
      e@9#0 = b@3#0 times a@1#0
      f@11#0 = e@9#0
      g@13#0 = c@5#0 minus d@7#0
      h@15#0 = e@9#0 divide f@11#0
      @T16#0 = call __printInt (g@13#0)
      @T17#0 = call __printInt (h@15#0)
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
