package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.StringType
import ml.dev.kotlin.latte.syntax.Type

private val STD_LIB_FUNCTIONS = HashSet<FunctionSignature>().apply {
  add("printInt" withArgs listOf(IntType))
  add("printString" withArgs listOf(StringType))
  add("error" withArgs listOf())
  add("readInt" withArgs listOf())
  add("readString" withArgs listOf())
}

infix fun String.mangled(args: List<Type>) =
  if (this withArgs args in STD_LIB_FUNCTIONS) this else "$this${args.joinToString("") { "@$it" }}"

data class FunctionSignature(val name: String, val args: List<Type>)

private infix fun String.withArgs(args: List<Type>) = FunctionSignature(this, args)
