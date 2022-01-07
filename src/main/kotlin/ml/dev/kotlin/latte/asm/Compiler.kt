package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.GlobalFlowAnalyzer
import ml.dev.kotlin.latte.quadruple.IR
import ml.dev.kotlin.latte.quadruple.Label
import ml.dev.kotlin.latte.quadruple.peepHoleOptimize
import ml.dev.kotlin.latte.typecheck.STD_LIB_FUNCTIONS

fun IR.compile(strategy: AllocatorStrategyProducer): String = Compiler(strategy).run { this@compile.compile() }

private class Compiler(
  private val strategy: AllocatorStrategyProducer,
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
      val analysis = GlobalFlowAnalyzer.analyzeToLinear(it).peepHoleOptimize()
      FunctionCompiler(analysis, strings, this, labelGenerator, strategy).compile()
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
