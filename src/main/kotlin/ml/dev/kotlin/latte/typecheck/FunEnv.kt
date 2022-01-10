package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.AstNode
import ml.dev.kotlin.latte.syntax.FunDefNode
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.DefaultMap
import ml.dev.kotlin.latte.util.FunEnvException
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.MutableDefaultMap

class FunEnv(
  private val argsCombinations: DefaultMap<List<Type>, Set<List<Type>>>,
  private val funEnv: MutableDefaultMap<String, HashMap<List<Type>, FunDeclaration>> = MutableDefaultMap({ HashMap() })
) {
  fun addFun(funDef: FunDefNode, inClass: String? = null): Unit = with(funDef) {
    val args = args.list.map { it.type }
    val mangled = (inClass?.let { "\$$it\$$ident" } ?: ident) mangled args
    if (args in funEnv[ident] && inClass == null) err("Redefined function $ident")
    funEnv[ident][args] = FunDeclaration(mangled, args, type)
    mangledName = mangled
  }

  operator fun get(name: String, args: List<Type>): FunDeclaration? =
    argsCombinations[args].mapNotNull { funEnv[name][it] }.singleOrNull()

  operator fun plusAssign(other: FunEnv): Unit = funEnv.putAll(other.funEnv.deepCopy { HashMap(it) })
}

private fun AstNode.err(message: String): Nothing = throw FunEnvException(LocalizedMessage(message, span?.from))

