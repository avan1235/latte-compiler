package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class OptimizeTest {
  @Test
  fun `test propagate constants and simplify with predefined variables`() = testOptimize(
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
  fun `test lcse with removed temp defs`() = testOptimize(
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

  @Test
  fun `test gcse with removed temp defs works locally as lcse`() = testOptimize(
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
    gcse = true,
  )

  @Test
  fun `test gcse with removed temp defs works on different blocks scopes`() = testOptimize(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
      int c = a + b;
      int d = a / b;
      int i = readInt();
      while (i > 0) {
        int result = (a + b) - (a / b);
        printInt(result);
        i--;
      }
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
      @T6#0 = a@1#0 div b@3#0
      d@7#0 = @T6#0
      @T8#0 = call __readInt ()
      i@9#0 = @T8#0
      goto L11
    L10:
      @T13#0 = a@1#0 plus b@3#0
      @T14#0 = a@1#0 div b@3#0
      @T15#0 = @T13#0 minus @T14#0
      result@16#0 = @T15#0
      @T17#0 = call __printInt (result@16#0)
      i@9#2 = dec i@9#1
    L11:
      i@9#1 = phi (L10:i@9#2, main:i@9#0)
      if i@9#1 gt 0 goto L10
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      a@1#0 = call __readInt ()
      b@3#0 = call __readInt ()
      c@5#0 = a@1#0 plus b@3#0
      d@7#0 = a@1#0 div b@3#0
      i@9#0 = call __readInt ()
      goto L11
    L10:
      @T13#0 = c@5#0
      @T14#0 = d@7#0
      result@16#0 = @T13#0 minus @T14#0
      @T17#0 = call __printInt (result@16#0)
      i@9#2 = dec i@9#1
    L11:
      i@9#1 = phi (L10:i@9#2, main:i@9#0)
      if i@9#1 gt 0 goto L10
      ret 0
    """,
    removeTempDefs = true,
    gcse = true,
  )

  @Test
  fun `test gcse with removed temp defs works on further blocks scopes`() = testOptimize(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
      int c = a + b;
      int d = a * b;
      int i = readInt();
      while (i > 0) {
        int result = (a + b) - (a * b);
        printInt(result);
        i--;
      }
      while (i < 42) {
        printInt(i);
        i++;
      }
      if (readInt() > 0) {
        int g = b + a;
        printInt(g);
      } else {
        int g = b * a;
        printInt(g);
      }
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
      @T6#0 = a@1#0 times b@3#0
      d@7#0 = @T6#0
      @T8#0 = call __readInt ()
      i@9#0 = @T8#0
      goto L11
    L10:
      @T13#0 = a@1#0 plus b@3#0
      @T14#0 = a@1#0 times b@3#0
      @T15#0 = @T13#0 minus @T14#0
      result@16#0 = @T15#0
      @T17#0 = call __printInt (result@16#0)
      i@9#4 = dec i@9#1
    L11:
      i@9#1 = phi (L10:i@9#4, main:i@9#0)
      if i@9#1 gt 0 goto L10
    L12:
      goto L19
    L18:
      @T21#0 = call __printInt (i@9#2)
      i@9#3 = inc i@9#2
    L19:
      i@9#2 = phi (L12:i@9#1, L18:i@9#3)
      if i@9#2 lt 42 goto L18
      @T25#0 = call __readInt ()
      if @T25#0 gt 0 goto L22
      @T26#0 = b@3#0 times a@1#0
      g@27#0 = @T26#0
      @T28#0 = call __printInt (g@27#0)
      goto L24
    L22:
      @T29#0 = b@3#0 plus a@1#0
      g@30#0 = @T29#0
      @T31#0 = call __printInt (g@30#0)
    L24:
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      a@1#0 = call __readInt ()
      b@3#0 = call __readInt ()
      c@5#0 = a@1#0 plus b@3#0
      d@7#0 = a@1#0 times b@3#0
      i@9#0 = call __readInt ()
      goto L11
    L10:
      @T13#0 = c@5#0
      @T14#0 = d@7#0
      result@16#0 = @T13#0 minus @T14#0
      @T17#0 = call __printInt (result@16#0)
      i@9#4 = dec i@9#1
    L11:
      i@9#1 = phi (L10:i@9#4, main:i@9#0)
      if i@9#1 gt 0 goto L10
    L12:
      goto L19
    L18:
      @T21#0 = call __printInt (i@9#2)
      i@9#3 = inc i@9#2
    L19:
      i@9#2 = phi (L12:i@9#1, L18:i@9#3)
      if i@9#2 lt 42 goto L18
      @T25#0 = call __readInt ()
      if @T25#0 gt 0 goto L22
      g@27#0 = d@7#0
      @T28#0 = call __printInt (g@27#0)
      goto L24
    L22:
      g@30#0 = c@5#0
      @T31#0 = call __printInt (g@30#0)
    L24:
      ret 0
    """,
    removeTempDefs = true,
    gcse = true,
  )

  @Test
  fun `test full optimization enabled`() = testOptimize(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
      int c = a + b;
      int d = a * b;
      int i = 42;
      while (i > 0) {
        int result = (a + b) - (b * a) + i;
        printInt(result);
        i--;
      }
      while (i < 42) { i++; }

      if (c > d) {
        int g = b + a;
        printInt(g);
      } else {
        int g = b * a;
        printInt(g);
      }

      if (d < c) {
        int g = a * b;
        printInt(g);
      } else {
        int g = a + b;
        printInt(g);
      }
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
      @T6#0 = a@1#0 times b@3#0
      d@7#0 = @T6#0
      i@8#0 = 42
      goto L10
    L9:
      @T12#0 = a@1#0 plus b@3#0
      @T13#0 = b@3#0 times a@1#0
      @T14#0 = @T12#0 minus @T13#0
      @T15#0 = @T14#0 plus i@8#1
      result@16#0 = @T15#0
      @T17#0 = call __printInt (result@16#0)
      i@8#4 = dec i@8#1
    L10:
      i@8#1 = phi (L9:i@8#4, main:i@8#0)
      if i@8#1 gt 0 goto L9
    L11:
      goto L19
    L18:
      i@8#3 = inc i@8#2
    L19:
      i@8#2 = phi (L11:i@8#1, L18:i@8#3)
      if i@8#2 lt 42 goto L18
      if c@5#0 gt d@7#0 goto L21
      @T24#0 = b@3#0 times a@1#0
      g@25#0 = @T24#0
      @T26#0 = call __printInt (g@25#0)
      goto L23
    L21:
      @T27#0 = b@3#0 plus a@1#0
      g@28#0 = @T27#0
      @T29#0 = call __printInt (g@28#0)
    L23:
      if d@7#0 lt c@5#0 goto L30
      @T33#0 = a@1#0 plus b@3#0
      g@34#0 = @T33#0
      @T35#0 = call __printInt (g@34#0)
      goto L32
    L30:
      @T36#0 = a@1#0 times b@3#0
      g@37#0 = @T36#0
      @T38#0 = call __printInt (g@37#0)
    L32:
      ret 0
    """,
    optimizedIrRepresentation = """
    main():
      a@1#0 = call __readInt ()
      b@3#0 = call __readInt ()
      c@5#0 = a@1#0 plus b@3#0
      d@7#0 = a@1#0 times b@3#0
      goto L10
    L9:
      @T14#0 = c@5#0 minus d@7#0
      result@16#0 = @T14#0 plus i@8#1
      @T17#0 = call __printInt (result@16#0)
      i@8#4 = dec i@8#1
    L10:
      i@8#1 = phi (L9:i@8#4, main:42)
      if i@8#1 gt 0 goto L9
    L11:
      goto L19
    L18:
      i@8#3 = inc i@8#2
    L19:
      i@8#2 = phi (L11:i@8#1, L18:i@8#3)
      if i@8#2 lt 42 goto L18
      if c@5#0 gt d@7#0 goto L21
      @T26#0 = call __printInt (d@7#0)
      goto L23
    L21:
      @T29#0 = call __printInt (c@5#0)
    L23:
      if d@7#0 lt c@5#0 goto L30
      @T35#0 = call __printInt (c@5#0)
      goto L32
    L30:
      @T38#0 = call __printInt (d@7#0)
    L32:
      ret 0
    """,
    removeTempDefs = true,
    simplifyExpr = true,
    removeDeadAssignQ = true,
    propagateConstants = true,
    lcse = true,
    gcse = true,
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
  val optimizedInstructions = graph.apply {
    optimize(removeTempDefs, propagateConstants, simplifyExpr, removeDeadAssignQ, lcse, gcse)
  }.instructions().asIterable()
  val repr = instructions.nlString { it.repr() }
  val optimizedRepr = optimizedInstructions.nlString { it.repr() }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr, "Invalid IR Representation")
  assertEquals("\n${optimizedIrRepresentation.trimIndent()}\n", optimizedRepr, "Invalid optimized IR Representation")
  assert(instructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
  assert(optimizedInstructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
}
