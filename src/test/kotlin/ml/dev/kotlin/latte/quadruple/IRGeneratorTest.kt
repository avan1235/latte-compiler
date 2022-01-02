package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class IRGeneratorTest {

  @Nested
  inner class BaseConstructTest {
    @Test
    fun `test decreases with special op`() = testIRRepr(
      program = """
      int main() {
        int a = 1;
        a--;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 1
        a@1#1 = dec a@1#0
        ret a@1#1
      """
    )

    @Test
    fun `test increases with special op`() = testIRRepr(
      program = """
      int main() {
        int a = -1;
        a++;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = -1
        a@1#1 = inc a@1#0
        ret a@1#1
      """
    )

    @Test
    fun `test check rel compare op with jumps`() = testIRRepr(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        boolean c = a < b;
        if (c) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = -1
        b@1#0 = 1
        @T0#0 = false
        if a@1#0 ge b@1#0 goto @F1
      @G5:
        @T0#2 = true
      @F1:
        @T0#1 = phi (main:@T0#0, @G5:@T0#2)
        c@1#0 = @T0#1
        if c@1#0 goto @L2
        ret 0
      @L2:
        ret 1
      """
    )

    @Test
    fun `test check rel equality op with jumps`() = testIRRepr(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        boolean c = a == b;
        if (c) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = -1
        b@1#0 = 1
        @T0#0 = false
        if a@1#0 ne b@1#0 goto @F1
      @G5:
        @T0#2 = true
      @F1:
        @T0#1 = phi (main:@T0#0, @G5:@T0#2)
        c@1#0 = @T0#1
        if c@1#0 goto @L2
        ret 0
      @L2:
        ret 1
      """
    )

    @Test
    fun `test check rel op in cond with jumps`() = testIRRepr(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        if (a < b && a == b) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = -1
        b@1#0 = 1
        if a@1#0 lt b@1#0 goto @M3
        goto @L1
      @M3:
        if a@1#0 eq b@1#0 goto @L0
      @L1:
        ret 0
      @L0:
        ret 1
      """
    )

    @Test
    fun `test generates functions other than main`() = testIRRepr(
      program = """
      int main() {
        int a = f();
        int b = g();
        return a + b;
      }
      int f() {
        return 42;
      }
      int g() {
        return 24;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call f ()
        a@1#0 = @T0#0
        @T1#0 = call g ()
        b@1#0 = @T1#0
        @T2#0 = a@1#0 plus b@1#0
        ret @T2#0
      f():
        ret 42
      g():
        ret 24
      """
    )
  }

  @Nested
  inner class FunctionTest {
    @Test
    fun `test not change std lib functions names`() = testIRRepr(
      program = """
      int main() {
        printString("str");
        printInt(42);
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __printString (@S1)
        @T2#0 = call __printInt (42)
        ret 0
      """,
      "str" to "@S1"
    )
  }

  @Nested
  inner class NestVariablesTest {
    @Test
    fun `variables in main scope have same index`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        int b = 1;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        b@1#0 = 1
        ret a@1#0
      """
    )

    @Test
    fun `variables index increases with scope`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        {
          int a = 1;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        a@2#0 = 1
        ret a@1#0
      """
    )

    @Test
    fun `variables out of scope can be modified`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        {
          a = 1;
          int a = 2;
          a = 3;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        a@1#1 = 1
        a@2#0 = 2
        a@2#1 = 3
        ret a@1#1
      """
    )

    @Test
    fun `scope increases with if statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        if (a == 1) {
          a = 1;
          int a = 2;
          a = 3;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        if a@1#0 eq 1 goto @L0
      @G2:
        goto @L1
      @L0:
        a@1#1 = 1
        a@3#0 = 2
        a@3#1 = 3
      @L1:
        a@1#2 = phi (@L0:a@1#1, @G2:a@1#0)
        ret a@1#2
      """
    )

    @Test
    fun `scope increases with if else statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        if (a == 1) {
          a = 1;
          int a = 2;
          a = 3;
        }
        else {
          a = 4;
          int a = 5;
          a = 6;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        if a@1#0 eq 1 goto @L0
      @L1:
        a@1#3 = 4
        a@3#3 = 5
        a@3#4 = 6
        goto @L2
      @L0:
        a@1#1 = 1
        a@3#0 = 2
        a@3#1 = 3
      @L2:
        a@1#2 = phi (@L0:a@1#1, @L1:a@1#3)
        a@3#2 = phi (@L0:a@3#1, @L1:a@3#4)
        ret a@1#2
      """
    )

    @Test
    fun `scope increases with while statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        while (a < 1) {
          a++;
          int a = 1;
          a = 2;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        goto @L1
      @L0:
        a@1#2 = inc a@1#1
        a@3#1 = 1
        a@3#2 = 2
      @L1:
        a@1#1 = phi (@L0:a@1#2, main:a@1#0)
        if a@1#1 lt 1 goto @L0
        ret a@1#1
      """
    )

    @Test
    fun `scope increases with single line if statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        if (a == 1) int a = 2;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        if a@1#0 eq 1 goto @L0
        goto @L1
      @L0:
        a@2#0 = 2
      @L1:
        ret a@1#0
      """
    )

    @Test
    fun `scope increases with single line if else statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        if (a == 1) int a = 2;
        else int a = 5;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        if a@1#0 eq 1 goto @L0
      @L1:
        a@2#2 = 5
        goto @L2
      @L0:
        a@2#0 = 2
      @L2:
        a@2#1 = phi (@L0:a@2#0, @L1:a@2#2)
        ret a@1#0
      """
    )

    @Test
    fun `scope increases with single line while statement`() = testIRRepr(
      program = """
      int main() {
        int a = 0;
        while (a < 1) int a = 1;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 0
        goto @L1
      @L0:
        a@2#1 = 1
      @L1:
        if a@1#0 lt 1 goto @L0
        ret a@1#0
      """
    )
  }

  @Nested
  inner class OpOnNotConstTest {
    @Test
    fun `test op on int`() = testIRRepr(
      program = """
      int main() {
        int a = 42;
        int b = 24;
        int x = (a + b * (3 - b) / a + 1) % 49;
        return x;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 42
        b@1#0 = 24
        @T1#0 = 3
        @T0#0 = @T1#0 minus b@1#0
        @T2#0 = b@1#0 times @T0#0
        @T3#0 = @T2#0 divide a@1#0
        @T4#0 = a@1#0 plus @T3#0
        @T5#0 = @T4#0 plus 1
        @T6#0 = @T5#0 mod 49
        x@1#0 = @T6#0
        ret x@1#0
      """
    )

    @Test
    fun `test op on boolean OR`() = testIRRepr(
      program = """
      int main() {
        boolean a = true;
        boolean b = false;
        boolean x = a || b;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = true
        b@1#0 = false
        if a@1#0 goto @L1
        if b@1#0 goto @L1
      @L2:
        @T0#2 = false
        goto @L3
      @L1:
        @T0#0 = true
      @L3:
        @T0#1 = phi (@L1:@T0#0, @L2:@T0#2)
        x@1#0 = @T0#1
        ret 0
      """
    )

    @Test
    fun `test op on boolean AND`() = testIRRepr(
      program = """
      int main() {
        boolean x = id(positive(1) && positive(-1));
        return 0;
      }
      boolean id(boolean a) {
        return a;
      }
      boolean positive(int a) {
        return a > 0;
      }
      """,
      irRepresentation = """
      main():
        @T6#0 = call positive@int (1)
        if @T6#0 goto @M5
        goto @L3
      @M5:
        @T7#1 = call positive@int (-1)
        if @T7#1 goto @L2
      @L3:
        @T1#0 = false
        goto @L4
      @L2:
        @T1#1 = true
      @L4:
        @T1#2 = phi (@L2:@T1#1, @L3:@T1#0)
        @T7#2 = phi (@L2:@T7#1, @L3:@T7#0)
        @T0#0 = call id@boolean (@T1#2)
        x@1#0 = @T0#0
        ret 0
      id@boolean(a#0):
        ret a#0
      positive@int(a#0):
        @T8#0 = false
        if a#0 le 0 goto @F9
      @G12:
        @T8#2 = true
      @F9:
        @T8#1 = phi (@G12:@T8#2, positive@int:@T8#0)
        ret @T8#1
      """
    )


    @Test
    fun `test op on string`() = testIRRepr(
      program = """
      int main() {
        string a = "42";
        string b = "24";
        string x = a + b + a;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = @S0
        b@1#0 = @S1
        @T2#0 = call __concatString (a@1#0, b@1#0)
        @T3#0 = call __concatString (@T2#0, a@1#0)
        x@1#0 = @T3#0
        ret 0
      """,
      "42" to "@S0",
      "24" to "@S1",
    )
  }


  @Nested
  inner class CondStructureTest {
    @Test
    fun `test structure of if`() = testIRRepr(
      program = """
      int main() {
        boolean b = true;
        if (b) return 1;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@1#0 = true
        if b@1#0 goto @L0
        goto @L1
      @L0:
        ret 1
      @L1:
        ret 0
      """
    )

    @Test
    fun `test structure of if else`() = testIRRepr(
      program = """
      int main() {
        boolean b = true;
        if (b) return 1;
        else return 0;
      }
      """,
      irRepresentation = """
      main():
        b@1#0 = true
        if b@1#0 goto @L0
        ret 0
      @L0:
        ret 1
      """
    )

    @Test
    fun `test structure of while`() = testIRRepr(
      program = """
      int main() {
        boolean b = true;
        int i = 0;
        while (b) i++;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@1#0 = true
        i@1#0 = 0
        goto @L1
      @L0:
        i@1#2 = inc i@1#1
      @L1:
        i@1#1 = phi (@L0:i@1#2, main:i@1#0)
        if b@1#0 goto @L0
        ret 0
      """
    )

    @Test
    fun `test structure of nested if else`() = testIRRepr(
      program = """
      int main() {
        boolean a = true;
        boolean b = true;
        if (b) {
          if (a) return 3;
          else return 2;
        }
        else {
          if (a) return 1;
          else return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = true
        b@1#0 = true
        if b@1#0 goto @L0
        if a@1#0 goto @L3
        ret 0
      @L3:
        ret 1
      @L0:
        if a@1#0 goto @L6
        ret 2
      @L6:
        ret 3
      """
    )
  }

  @Nested
  inner class CondSimplifyTest {
    @Test
    fun `test simplify const if boolean AND - remove`() = testIRRepr(
      program = """
      int main() {
        if (false && true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if boolean AND - leave`() = testIRRepr(
      program = """
      int main() {
        if (true && true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify const if boolean OR - remove`() = testIRRepr(
      program = """
      int main() {
        if (false || false) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if boolean OR - leave`() = testIRRepr(
      program = """
      int main() {
        if (false || true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify const if nested - remove all`() = testIRRepr(
      program = """
      int main() {
        if ((true && false) || 1 > 2) {
          if ((2 >= 3) || 1 == 2) {
            if (false || 1 != 1) {
              return 1;
            }
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if nested - remove only nested`() = testIRRepr(
      program = """
      int main() {
        if ((true && false) || 3 > 2) {
          if (!(2 < 3) || 1 == 2) {
            return 2;
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify mixed if nested - remove only nested`() = testIRRepr(
      program = """
      int main() {
        boolean a = (true && false) || 3 > 2;
        if (a) {
          if (!(2 < 3) || 1 == 2) {
            return 2;
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = true
        a@1#0 = @T0#0
        if a@1#0 goto @L6
        goto @L7
      @L6:
        ret 1
      @L7:
        ret 0
      """
    )

    @Test
    fun `test simplify const while - on true`() = testIRRepr(
      program = """
      int main() {
        int i = 0;
        int j = 0;
        while (false || (true == true)) {
          i++;
          j--;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        i@1#0 = 0
        j@1#0 = 0
        goto @L1
      @L0:
        i@1#2 = inc i@1#1
        j@1#2 = dec j@1#1
      @L1:
        j@1#1 = phi (@L0:j@1#2, main:j@1#0)
        i@1#1 = phi (@L0:i@1#2, main:i@1#0)
        goto @L0
      """
    )

    @Test
    fun `test simplify const if else - on true`() = testIRRepr(
      program = """
      int main() {
        if (1 == 2 || (true && (4 > 3))) {
          return 2;
        }
        else {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 2
      """
    )

    @Test
    fun `test simplify const if else - on false`() = testIRRepr(
      program = """
      int main() {
        if (1 == 2 || (true && false)) {
          return 2;
        }
        else {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify identity equal memory locations`() = testIRRepr(
      program = """
      int main() {
        int a = 42;
        if (a == a) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 42
        ret 1
      """
    )

    @Test
    fun `test simplify identity not equal memory locations`() = testIRRepr(
      program = """
      int main() {
        int a = 42;
        if (a != a) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = 42
        ret 0
      """
    )
  }

  @Nested
  inner class ConstSimplifyTest {
    @Test
    fun `test simplify const int`() = testIRRepr(
      program = """
      int main() {
        int i = (100 + 2 * (3 - 1) / 2 + 1) % 49;
        return i;
      }
      """,
      irRepresentation = """
      main():
        i@1#0 = 5
        ret i@1#0
      """
    )

    @Test
    fun `test simplify const neg int`() = testIRRepr(
      program = """
      int main() {
        int i = -(-(-(-10)));
        return i;
      }
      """,
      irRepresentation = """
      main():
        i@1#0 = 10
        ret i@1#0
      """
    )

    @Test
    fun `test simplify const boolean`() = testIRRepr(
      program = """
      int main() {
        boolean b = true && (false || (true && true));
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = true
        b@1#0 = @T0#0
        ret 0
      """
    )

    @Test
    fun `test simplify const not boolean`() = testIRRepr(
      program = """
      int main() {
        boolean b = !(!(!(!true)));
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@1#0 = true
        ret 0
      """
    )

    @Test
    fun `test simplify const string`() = testIRRepr(
      program = """
      int main() {
        string s = "left" + "<>" + "right";
        return 0;
      }
      """,
      irRepresentation = """
      main():
        s@1#0 = @S4
        ret 0
      """,
      "left" to "@S0",
      "<>" to "@S1",
      "left<>" to "@S2",
      "right" to "@S3",
      "left<>right" to "@S4"
    )
  }
}

private fun testIRRepr(
  program: String,
  irRepresentation: String,
  vararg strings: Pair<String, String>,
  optimize: Boolean = true
) {
  val (graph, str) = program.byteInputStream().parse().typeCheck().toIR()
  val phony = graph.orderedBlocks().associate { block -> block.label to block.phony }
  val instructions = graph.orderedBlocks().flatMap { it.statements }.asSequence().flatMap {
    sequence {
      yield(it)
      if (it is Labeled) phony[it.label]?.sortedBy { it.to.idx }?.forEach { yield(it) }
    }
  }.asIterable().run { if (optimize) peepHoleOptimize() else this }
  val repr = instructions.nlString { it.repr() }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr)
  assertEquals(strings.toMap(), str.mapValues { it.value.name })
  assert(instructions.isSSA())
}

private fun Iterable<Quadruple>.isSSA(): Boolean =
  mapNotNull { it.definedVar()?.repr() }.let { it.size == it.toHashSet().size }
