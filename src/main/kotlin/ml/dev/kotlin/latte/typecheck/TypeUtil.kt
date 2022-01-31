package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.syntax.Type


const val ENTRY_LABEL: String = "main"
val NO_ARGS: List<Type> = emptyList()

private val STD_LIB = setOf(
  FunDeclaration("printInt", listOf(IntType), VoidType),
  FunDeclaration("printString", listOf(StringType), VoidType),
  FunDeclaration("error", NO_ARGS, VoidType),
  FunDeclaration("readInt", NO_ARGS, IntType),
  FunDeclaration("readString", NO_ARGS, StringType),
  FunDeclaration("concatString", listOf(StringType, StringType), StringType),
  FunDeclaration("alloc", listOf(IntType), VoidRefType),
)

private val STD_LIB_FUNCTIONS_SIGNATURES: Set<FunSignature> = STD_LIB.mapTo(HashSet()) { it.name with it.args }

val STD_LIB_FUNCTIONS: Map<String, FunDeclaration> = STD_LIB.associateBy { it.name mangled it.args }

fun createStdLibFunEnv(): LinkedHashMap<FunSignature, FunDeclaration> = STD_LIB.associateTo(LinkedHashMap()) {
  FunSignature(it.name, it.args) to FunDeclaration(it.name mangled it.args, it.args, it.ret)
}

infix fun String.mangled(args: List<Type>): String =
  if (this with args in STD_LIB_FUNCTIONS_SIGNATURES) "__$this" else "$this${args.joinToString("") { "$ARG_SEP$it" }}"

const val ARG_SEP: String = "$$"

data class FunSignature(val name: String, val args: List<Type>)
data class FunDeclaration(val name: String, val args: List<Type>, val ret: Type)

private infix fun String.with(args: List<Type>): FunSignature = FunSignature(this, args)
