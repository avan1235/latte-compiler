package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class IRGeneratorTest {

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
  fun `test simplify const int`() = testIRRepr(
    program = """
    int main() {
      int i = (100 + 2 * (3 - 1) / 2) % 49;
      return i;
    }
    """,
    ir = """
    main():
      V0 = 4
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
      V0 = "left<>right"
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

private fun testIRRepr(program: String, ir: String, vararg strings: Pair<String, String>) {
  val quadruples = program.byteInputStream().parse().typeCheck().toIR()
  val irRepr = quadruples.list.joinToString("\n") { it.repr() }
  assertEquals(ir.trimIndent(), irRepr)
  assertEquals(strings.toMap(), quadruples.strings.mapValues { it.value.name })
}

private fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> int.toString()
  is StringConstValue -> "\"$str\""
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
  is DecQ -> "dec ${label.repr()}"
  is IncQ -> "inc ${label.repr()}"
  is FunCallQ -> "${to.repr()} = call ${label.name} (${args.joinToString { it.repr() }})"
  is RetQ -> "ret${valueHolder?.let { " ${it.repr()}" } ?: ""}"
}.let { if (this is LabelQuadruple) it else "  $it" }
