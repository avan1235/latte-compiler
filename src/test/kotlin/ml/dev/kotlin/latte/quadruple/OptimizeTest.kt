package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OptimizeTest {
  @Test
  fun `propagate constants and simplify with predefined variables`() = testOptimize(
    program = """
    int main() {
      int a = 42;
      int b = 24;
      int c = a + b;
      int d = c / 2;
      int e = -d;
      int sum = a + b + c + d + e;
      printInt(sum);
      return 0;
    }
    """,
    irRepresentation = """
    main():
      a@0#0 = 42
      b@1#0 = 24
      @T2#0 = a@0#0 plus b@1#0
      c@3#0 = @T2#0
      @T4#0 = c@3#0 div 2
      d@5#0 = @T4#0
      @T6#0 = neg d@5#0
      e@7#0 = @T6#0
      @T8#0 = a@0#0 plus b@1#0
      @T9#0 = @T8#0 plus c@3#0
      @T10#0 = @T9#0 plus d@5#0
      @T11#0 = @T10#0 plus e@7#0
      sum@12#0 = @T11#0
      @T13#0 = call __printInt (sum@12#0)
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      @T13#0 = call __printInt (132)
      ret 0
    """,
    removeTempDefs = true,
    propagateConstants = true,
    simplifyExpr = true,
    removeDeadAssignQ = true,
  )

  @Test
  fun `lcse with removed temp defs`() = testOptimize(
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
      @T14#0 = e@9#0 div f@11#0
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
      h@15#0 = e@9#0 div f@11#0
      @T16#0 = call __printInt (g@13#0)
      @T17#0 = call __printInt (h@15#0)
      ret 0
    """,
    removeTempDefs = true,
    lcse = true,
  )
}

private fun testOptimize(
  program: String,
  irRepresentation: String,
  optimizedIrRepresentation: String,
  removeTempDefs: Boolean = false,
  propagateConstants: Boolean = false,
  simplifyExpr: Boolean = false,
  removeDeadAssignQ: Boolean = false,
  lcse: Boolean = false,
  gcse: Boolean = false,
) {
  val (graph, _, _) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
  }
  val instructions = graph.instructions().asIterable()
  val lcseInstructions = graph.apply {
    optimize(removeTempDefs, propagateConstants, simplifyExpr, removeDeadAssignQ, lcse, gcse)
  }.instructions().asIterable()
  val repr = instructions.nlString { it.repr() }
  val lcseRepr = lcseInstructions.nlString { it.repr() }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr, "Invalid IR Representation")
  assertEquals("\n${optimizedIrRepresentation.trimIndent()}\n", lcseRepr, "Invalid optimized IR Representation")
  assert(instructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
  assert(lcseInstructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
}
