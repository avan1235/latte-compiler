package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.parse
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
      ir = """
      main():
        T0 = 0
        ret T0
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
      ir = """
      main():
        V0 = 1
        T1 = 1
        T0 = V0 minus T1
        V0 = T0
        T2 = 0
        ret T2
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
      ir = """
      main():
        V0 = 1
        T1 = 1
        T0 = V0 plus T1
        V0 = T0
        T2 = 0
        ret T2
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
      ir = """
      main():
        V0 = 42
        V1 = 24
        T1 = 3
        T0 = T1 minus V1
        T2 = V1 times T0
        T3 = T2 divide V0
        T4 = V0 plus T3
        T6 = 1
        T5 = T4 plus T6
        T8 = 49
        T7 = T5 mod T8
        V2 = T7
        ret V2
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
      ir = """
      main():
        V0 = true
        V1 = false
        if V0 goto L1
      M4:
        if V1 goto L1
      L2:
        T0 = false
        goto L3
      L1:
        T0 = true
      L3:
        V2 = T0
        T5 = 0
        ret T5
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
      ir = """
      main():
        V0 = true
        V1 = false
        if V0 goto M4
        goto L2
      M4:
        if V1 goto L1
      L2:
        T0 = false
        goto L3
      L1:
        T0 = true
      L3:
        V2 = T0
        T5 = 0
        ret T5
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
      ir = """
      main():
        V0 = S0
        V1 = S1
        T2 = V0 plus V1
        T3 = T2 plus V0
        V2 = T3
        T4 = 0
        ret T4
      """,
      "42" to "S0",
      "24" to "S1",
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
      ir = """
      main():
        V0 = 5
        ret V0
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
      ir = """
      main():
        V0 = true
        T0 = 0
        ret T0
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
      ir = """
      main():
        V0 = S4
        T5 = 0
        ret T5
      """,
      "left" to "S0",
      "<>" to "S1",
      "left<>" to "S2",
      "right" to "S3",
      "left<>right" to "S4",
    )
  }
}

private fun testIRRepr(program: String, ir: String, vararg strings: Pair<String, String>) {
  val quadruples = program.byteInputStream().parse().typeCheck().toIR()
  val irRepr = quadruples.list.joinToString("\n", "\n", "\n") { it.repr() }
  assertEquals("\n${ir.trimIndent()}\n", irRepr)
  assertEquals(strings.toMap(), quadruples.strings.mapValues { it.value.name })
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
    is CodeFunLabelQ -> null
    is CodeLabelQ -> null
    is RetQ -> null
    is UnOpQ -> it.to
  }?.repr()
}.let { it.size == it.toSet().size }

private fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> int.toString()
  is StringConstValue -> label.name
  is ArgValue -> "A$idx"
  is LocalValue -> "V$idx"
  is TempValue -> label.name
}

private fun Quadruple.repr(): String = when (this) {
  is AssignQ -> "${to.repr()} = ${from.repr()}"
  is BiCondJumpQ -> "if ${left.repr()} ${op.name.lowercase()} ${right.repr()} goto ${onTrue.name}"
  is BinOpQ -> "${to.repr()} = ${left.repr()} ${op.name.lowercase()} ${right.repr()}"
  is UnOpQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is CodeFunLabelQ -> "${label.name}(${args.joinToString { it.repr() }}):"
  is CodeLabelQ -> "${label.name}:"
  is CondJumpQ -> "if ${cond.repr()} goto ${onTrue.name}"
  is JumpQ -> "goto ${label.name}"
  is FunCallQ -> "${to.repr()} = call ${label.name} (${args.joinToString { it.repr() }})"
  is RetQ -> "ret${valueHolder?.let { " ${it.repr()}" } ?: ""}"
}.let { if (this is LabelQuadruple) it else "  $it" }
