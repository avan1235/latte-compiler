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
    fun `test returns using temp variables`() = testIRRepr(
      program = """
      int main() {
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0 = 0
        ret @T0
      """
    )

    @Test
    fun `test decreases with bin op`() = testIRRepr(
      program = """
      int main() {
        int a = 1;
        a--;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a = 1
        @T1 = 1
        @T0 = a minus @T1
        a = @T0
        @T2 = 0
        ret @T2
      """
    )

    @Test
    fun `test increases with bin op`() = testIRRepr(
      program = """
      int main() {
        int a = 1;
        a++;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a = 1
        @T1 = 1
        @T0 = a plus @T1
        a = @T0
        @T2 = 0
        ret @T2
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
        a = 42
        b = 24
        @T1 = 3
        @T0 = @T1 minus b
        @T2 = b times @T0
        @T3 = @T2 divide a
        @T4 = a plus @T3
        @T6 = 1
        @T5 = @T4 plus @T6
        @T8 = 49
        @T7 = @T5 mod @T8
        x = @T7
        ret x
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
        a = true
        b = false
        if a goto @L1
        if b goto @L1
        @T0 = false
        goto @L3
      @L1:
        @T0 = true
      @L3:
        x = @T0
        @T5 = 0
        ret @T5
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
        a = true
        b = false
        if a goto @M4
        goto @L2
      @M4:
        if b goto @L1
      @L2:
        @T0 = false
        goto @L3
      @L1:
        @T0 = true
      @L3:
        x = @T0
        @T5 = 0
        ret @T5
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
        a = @S0
        b = @S1
        @T2 = a plus b
        @T3 = @T2 plus a
        x = @T3
        @T4 = 0
        ret @T4
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
        @T4 = 0
        ret @T4
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
        @T3 = 1
        ret @T3
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
        @T4 = 0
        ret @T4
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
        @T3 = 1
        ret @T3
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
        @T12 = 0
        ret @T12
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
        @T8 = 1
        ret @T8
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
        a = true
        if a goto @L0
        goto @L1
      @L0:
        @T6 = 1
        ret @T6
      @L1:
        @T7 = 0
        ret @T7
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
        @T6 = 2
        ret @T6
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
        @T5 = 1
        ret @T5
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
        i = 5
        ret i
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
        b = true
        @T0 = 0
        ret @T0
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
        s = @S4
        @T5 = 0
        ret @T5
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
  is RetQ -> "ret${valueHolder?.let { " ${it.repr()}" } ?: ""}"
}.let { if (this is LabelQ) it else "  $it" }
