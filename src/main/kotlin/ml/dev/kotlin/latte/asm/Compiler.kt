package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.Bytes
import ml.dev.kotlin.latte.syntax.PrimitiveType.IntType
import ml.dev.kotlin.latte.syntax.PrimitiveType.VoidRefType
import ml.dev.kotlin.latte.syntax.Type
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
    result.appendLine("section .data")
    strings.defineUsedStrings(code)
    vTables.defineVTables()
    result.appendLine("section .text")
    result.appendLine(code)
    return result.toString()
  }

  private fun IR.compileFunctions(): String = buildString {
    graph.functions.values.forEach {
      val analysis = GlobalFlowAnalyzer.analyzeToLinear(it).peepHoleOptimize()
      FunctionCompiler(analysis, strings, vTables, this, labelGenerator, strategy).compile()
    }
  }

  private fun Map<String, Label>.defineUsedStrings(code: String) {
    entries.forEach { (string, label) ->
      if (!label.name.toRegex().containsMatchIn(code)) return@forEach
      result.append(label.name).append(": db ")
      val bytes = string.toByteArray()
      bytes.joinTo(result, separator = ", ")
      if (bytes.isNotEmpty()) result.appendLine(", 0") else result.appendLine("0")
    }
  }

  private fun Map<Type, VirtualTable>.defineVTables() {
    entries.forEach { (classType, vTable) ->
      result.append(classType.typeName).append(": dd ")
      vTable.declarations.joinTo(result, separator = ", ") { it.name }
      result.appendLine()
    }
  }
}

val CLASS_FIELDS_OFFSET: Bytes = VoidRefType.size
val CLASS_METHOD_ARGS_OFFSET: Bytes = VoidRefType.size

const val THIS_ARG_ID: String = "self"
val ALLOC_FUN_LABEL: Label = "__alloc".label
val CONCAT_STRING_FUN_LABEL: Label = "__concatString".label
val EMPTY_STRING_LABEL: Label = "S@EMPTY".label


