package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.IR
import ml.dev.kotlin.latte.quadruple.Label
import ml.dev.kotlin.latte.typecheck.STD_LIB_FUNCTIONS
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import ml.dev.kotlin.latte.util.unit

fun IR.compile(): String = Compiler().run { this@compile.compile() }

private class Compiler(
  private val result: StringBuilder = StringBuilder(),
) {

  fun IR.compile(): String {
    val functions = graph.orderedBlocks().splitAt(first = { it.isStart })
    functions.forEach { it.firstOrNull()?.label?.defineGlobal() }
    result.appendLine(STD_LIB_FUNCTIONS.keys.nlString { "extern $it" })
    result.appendLine("section .data")
    strings.defineDbStrings()
    result.appendLine("section .text")
    functions.forEach { FunctionCompiler(it, strings, result, labelGenerator).compile() }
    return result.toString()
  }

  private fun Label.defineGlobal(): Unit = result.append("global ").appendLine(name).unit()

  private fun Map<String, Label>.defineDbStrings() {
    entries.forEach { (string, label) ->
      result.append(label.name).append(": db ")
      val bytes = string.toByteArray()
      bytes.joinTo(result, separator = ", ")
      if (bytes.isNotEmpty()) result.appendLine(", 0") else result.appendLine("0")
    }
  }
}
