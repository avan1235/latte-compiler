package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.PrimitiveType.IntType
import ml.dev.kotlin.latte.syntax.PrimitiveType.StringType
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.syntax.PrimitiveType.VoidType

private val STD_LIB_FUNCTIONS_SIGNATURES = setOf(
  "printInt" withArgs listOf(IntType),
  "printString" withArgs listOf(StringType),
  "error" withArgs listOf(),
  "readInt" withArgs listOf(),
  "readString" withArgs listOf(),
)

val STD_LIB_FUNCTIONS = mapOf(
  "__printInt" to VoidType,
  "__printString" to VoidType,
  "__error" to VoidType,
  "__readInt" to IntType,
  "__readString" to StringType,
  "__concatString" to StringType,
)

const val ENTRY_LABEL: String = "main"

infix fun String.mangled(args: List<Type>): String =
  if (this withArgs args in STD_LIB_FUNCTIONS_SIGNATURES) "__$this" else "$this${args.joinToString("") { "@$it" }}"

data class FunctionSignature(val name: String, val args: List<Type>)

private infix fun String.withArgs(args: List<Type>) = FunctionSignature(this, args)
