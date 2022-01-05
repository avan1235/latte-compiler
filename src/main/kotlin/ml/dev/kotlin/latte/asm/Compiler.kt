package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.typecheck.STD_LIB_FUNCTIONS
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.unit

fun IR.compile(): String = Compiler().run { this@compile.compile() }

private class Compiler(
  private val result: StringBuilder = StringBuilder(),
) {

  fun IR.compile(): String {
    STD_LIB_FUNCTIONS.keys.forEach { result.append("extern ").appendLine(it) }
    graph.functions.keys.forEach { result.append("global ").appendLine(it.name) }
    val code = compileFunctions()
    strings.defineUsedStrings(code)
    result.appendLine("section .text")
    result.appendLine(code)
    return result.toString()
  }

  private fun IR.compileFunctions(): String = buildString {
    graph.functions.values.forEach {
      val analysis = GlobalFlowAnalyzer.analyze(it).peepHoleOptimize()
      println(analysis.statements.nlString { it.repr() })
      FunctionCompiler(analysis, strings, this, labelGenerator).compile()
    }
  }

  private fun Map<String, Label>.defineUsedStrings(code: String) {
    result.appendLine("section .data")
    entries.forEach { (string, label) ->
      if (!label.name.toRegex().containsMatchIn(code)) return@forEach
      result.append(label.name).append(": db ")
      val bytes = string.toByteArray()
      bytes.joinTo(result, separator = ", ")
      if (bytes.isNotEmpty()) result.appendLine(", 0") else result.appendLine("0")
    }
  }
}

private fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> "$int"
  is StringConstValue -> label.name
  is ArgValue -> id
  is LocalValue -> id
}

private fun Quadruple.repr(): String = when (this) {
  is AssignQ -> "${to.repr()} = ${from.repr()}"
  is RelCondJumpQ -> "if ${left.repr()} ${op.name.lowercase()} ${right.repr()} goto ${toLabel.name}"
  is BinOpQ -> "${to.repr()} = ${left.repr()} ${op.name.lowercase()} ${right.repr()}"
  is UnOpQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is FunCodeLabelQ -> "${label.name}(${args.joinToString { it.repr() }}):"
  is CodeLabelQ -> "${label.name}:"
  is CondJumpQ -> "if ${cond.repr()} goto ${toLabel.name}"
  is JumpQ -> "goto ${toLabel.name}"
  is FunCallQ -> "${to.repr()} = call ${label.name} (${args.joinToString { it.repr() }})"
  is RetQ -> "ret${value?.let { " ${it.repr()}" } ?: ""}"
  is UnOpModQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is PhonyQ -> "${to.repr()} = phi (${from.toList().joinToString(", ") { "${it.first.name}:${it.second.repr()}" }})"
}.let { if (this is Labeled) it else "  $it" }
