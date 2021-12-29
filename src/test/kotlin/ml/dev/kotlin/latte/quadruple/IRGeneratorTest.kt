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
        a@1 = 1
        dec a@1
        ret a@1
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
        a@1 = -1
        inc a@1
        ret a@1
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
        @T0 = call printString (@S1)
        @T2 = call printInt (42)
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
        a@1 = 0
        b@1 = 1
        ret a@1
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
        a@1 = 0
        a@2 = 1
        ret a@1
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
        a@1 = 0
        a@1 = 1
        a@2 = 2
        a@2 = 3
        ret a@1
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
        a@1 = 0
        if a@1 eq 1 goto @L0
        goto @L1
      @L0:
        a@1 = 1
        a@3 = 2
        a@3 = 3
      @L1:
        ret a@1
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
        a@1 = 0
        if a@1 eq 1 goto @L0
        a@1 = 4
        a@3 = 5
        a@3 = 6
        goto @L2
      @L0:
        a@1 = 1
        a@3 = 2
        a@3 = 3
      @L2:
        ret a@1
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
        a@1 = 0
        goto @L1
      @L0:
        inc a@1
        a@3 = 1
        a@3 = 2
      @L1:
        if a@1 lt 1 goto @L0
        ret a@1
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
        a@1 = 0
        if a@1 eq 1 goto @L0
        goto @L1
      @L0:
        a@2 = 2
      @L1:
        ret a@1
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
        a@1 = 0
        if a@1 eq 1 goto @L0
        a@2 = 5
        goto @L2
      @L0:
        a@2 = 2
      @L2:
        ret a@1
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
        a@1 = 0
        goto @L1
      @L0:
        a@2 = 1
      @L1:
        if a@1 lt 1 goto @L0
        ret a@1
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
        a@1 = 42
        b@1 = 24
        @T1 = 3
        @T0 = @T1 minus b@1
        @T2 = b@1 times @T0
        @T3 = @T2 divide a@1
        @T4 = a@1 plus @T3
        @T5 = @T4 plus 1
        @T6 = @T5 mod 49
        x@1 = @T6
        ret x@1
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
        a@1 = true
        b@1 = false
        if a@1 goto @L1
        if b@1 goto @L1
        @T0 = false
        goto @L3
      @L1:
        @T0 = true
      @L3:
        x@1 = @T0
        ret 0
      """
    )

    @Test
    fun `test op on boolean AND`() = testIRRepr(
      program = """
      int main() {
        boolean a = true;
        boolean b = false;
        boolean x = a && b;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1 = true
        b@1 = false
        if a@1 goto @M4
        goto @L2
      @M4:
        if b@1 goto @L1
      @L2:
        @T0 = false
        goto @L3
      @L1:
        @T0 = true
      @L3:
        x@1 = @T0
        ret 0
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
        a@1 = @S0
        b@1 = @S1
        @T2 = a@1 plus b@1
        @T3 = @T2 plus a@1
        x@1 = @T3
        ret 0
      """,
      "42" to "@S0",
      "24" to "@S1",
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
        a@1 = true
        if a@1 goto @L0
        goto @L1
      @L0:
        ret 1
      @L1:
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
        i@1 = 0
        j@1 = 0
        goto @L1
      @L0:
        inc i@1
        dec j@1
      @L1:
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
        i@1 = 5
        ret i@1
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
        i@1 = 10
        ret i@1
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
        b@1 = true
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
        s@1 = @S4
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
  val instructions = graph.orderedBlocks().flatMap { it.instructions }.run { if (optimize) optimize() else this }
  val repr = instructions.nlString { it.repr() }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr)
  assertEquals(strings.toMap(), str.mapValues { it.value.name })
  // assert(quadruples.list.isSSA())
}

private fun List<Quadruple>.isSSA(): Boolean = mapNotNull {
  when (it) {
    is AssignQ -> it.to
    is BiCondJumpQ -> null
    is BinOpQ -> it.to
    is CondJumpQ -> null
    is FunCallQ -> it.to
    is JumpQ -> null
    is FunCodeLabelQ -> null
    is CodeLabelQ -> null
    is RetQ -> null
    is UnOpQ -> it.to
    is DecQ -> it.toFrom
    is IncQ -> it.toFrom
  }?.repr()
}.let { it.size == it.toSet().size }

private fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> int.toString()
  is StringConstValue -> label.name
  is ArgValue -> name
  is LocalValue -> name
  is TempValue -> name
}

private fun Quadruple.repr(): String = when (this) {
  is AssignQ -> "${to.repr()} = ${from.repr()}"
  is BiCondJumpQ -> "if ${left.repr()} ${op.name.lowercase()} ${right.repr()} goto ${toLabel.name}"
  is BinOpQ -> "${to.repr()} = ${left.repr()} ${op.name.lowercase()} ${right.repr()}"
  is UnOpQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is FunCodeLabelQ -> "${label.name}(${args.joinToString { it.repr() }}):"
  is CodeLabelQ -> "${label.name}:"
  is CondJumpQ -> "if ${cond.repr()} goto ${toLabel.name}"
  is JumpQ -> "goto ${toLabel.name}"
  is FunCallQ -> "${to.repr()} = call ${label.name} (${args.joinToString { it.repr() }})"
  is RetQ -> "ret${value?.let { " ${it.repr()}" } ?: ""}"
  is DecQ -> "dec ${toFrom.repr()}"
  is IncQ -> "inc ${toFrom.repr()}"
}.let { if (this is LabelQ) it else "  $it" }
