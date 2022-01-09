package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.AstNode
import ml.dev.kotlin.latte.syntax.ClassType
import ml.dev.kotlin.latte.syntax.FunDefNode
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.FunEnvException
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.combinations

data class FunEnv(
  private val hierarchy: ClassHierarchy,
  private val funEnv: MutableDefaultMap<String, HashMap<List<Type>, FunDeclaration>> = MutableDefaultMap({ HashMap() })
) {

  fun addFun(funDef: FunDefNode): Unit = with(funDef) {
    val args = args.list.map { it.type }
    val name = ident mangled args
    if (name in funEnv) err("Redefined function $ident")
    funEnv[ident][args] = FunDeclaration(name, args, type)
    mangledName = name
  }

  fun AstNode.getFunMatching(name: String, args: List<Type>): FunDeclaration? {
    val forName = funEnv[name]
    val typesCombinations = args.map { argType ->
      if (argType is ClassType) hierarchy.orderedClassParents(argType.typeName).map { ClassType(it) }
      else listOf(argType)
    }.combinations()
    val returnTypes = typesCombinations.mapNotNull { forName[it] }
//    if (returnTypes.size > 1)
    return null
  }
}

private fun AstNode.err(message: String): Nothing = throw FunEnvException(LocalizedMessage(message, span?.from))

