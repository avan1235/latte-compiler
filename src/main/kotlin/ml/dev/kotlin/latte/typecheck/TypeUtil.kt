package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.MutableDefaultMap


const val ENTRY_LABEL: String = "main"
val NO_ARGS: List<Type> = emptyList()

val STD_LIB = setOf(
  FunDeclaration("printInt", listOf(IntType), VoidType),
  FunDeclaration("printString", listOf(StringType), VoidType),
  FunDeclaration("error", NO_ARGS, VoidType),
  FunDeclaration("readInt", NO_ARGS, IntType),
  FunDeclaration("readString", NO_ARGS, StringType),
  FunDeclaration("concatString", listOf(StringType, StringType), StringType),
)

private val STD_LIB_BY_NAME: Map<String, FunDeclaration> = STD_LIB.associateBy { it.name }

private val STD_LIB_FUNCTIONS_SIGNATURES: Set<FunSignature> = STD_LIB.mapTo(HashSet()) { it.name with it.args }

val STD_LIB_FUNCTIONS: Map<String, FunDeclaration> = STD_LIB.associateBy { "__${it.name}" }

fun createStdLibFunEnv(): MutableDefaultMap<String, HashMap<List<Type>, FunDeclaration>> = MutableDefaultMap({
  STD_LIB_BY_NAME[it]?.let { HashMap<List<Type>, FunDeclaration>().apply { put(it.args, it) } } ?: HashMap()
})

infix fun String.mangled(args: List<Type>): String =
  if (this with args in STD_LIB_FUNCTIONS_SIGNATURES) "__$this" else "$this${args.joinToString("") { "@$it" }}"

data class FunSignature(val name: String, val args: List<Type>)
data class FunDeclaration(val name: String, val args: List<Type>, val ret: Type)

private infix fun String.with(args: List<Type>): FunSignature = FunSignature(this, args)
