package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.AstNode
import ml.dev.kotlin.latte.syntax.FunDefNode
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.FunEnvException
import ml.dev.kotlin.latte.util.LocalizedMessage

data class FunEnv(
  private val argsCombinations: DefaultMap<List<Type>, Set<List<Type>>>,
  private val funEnv: LinkedHashMap<FunSignature, FunDeclaration> = LinkedHashMap()
) {
  fun ordered(): List<FunDeclaration> = funEnv.values.toList()

  operator fun get(name: String, args: List<Type>): FunDeclaration? =
    argsCombinations[args].mapNotNull { funEnv[FunSignature(name, it)] }.singleOrNull()

  operator fun set(inClass: String, funDef: FunDefNode): Unit = addFun(funDef, inClass)

  operator fun plusAssign(funDef: FunDefNode): Unit = addFun(funDef)

  operator fun plusAssign(other: FunEnv): Unit = funEnv.putAll(other.funEnv)

  private fun addFun(funDef: FunDefNode, inClass: String? = null): Unit = with(funDef) {
    val args = args.list.map { it.type }
    val mangled = (inClass?.let { "$it::$ident" } ?: ident) mangled args
    val sign = FunSignature(ident, args)
    if (sign in funEnv && inClass == null) err("Redefined function $ident")
    funEnv[sign] = FunDeclaration(mangled, args, type)
    mangledName = mangled
  }
}

private fun AstNode.err(message: String): Nothing = throw FunEnvException(LocalizedMessage(message, span?.from))

