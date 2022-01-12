package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.Named
import ml.dev.kotlin.latte.syntax.Bytes
import ml.dev.kotlin.latte.syntax.PrimitiveType.VoidRefType
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.typecheck.FunDeclaration
import ml.dev.kotlin.latte.typecheck.FunEnv
import ml.dev.kotlin.latte.util.LatteIllegalStateException
import ml.dev.kotlin.latte.util.msg

data class VirtualTable(
  val declarations: List<FunDeclaration>,
  private val className: String,
  private val classMethods: FunEnv,
) {
  private val addressed: Map<FunDeclaration, Bytes> = run {
    var address = 0
    declarations.associateWith { address.also { address += VoidRefType.size } }
  }

  operator fun get(name: String, args: List<Type>): Bytes =
    classMethods[name, args]?.let { addressed[it] }
      ?: throw LatteIllegalStateException("Not defined function $className$$name(${args.joinToString()})".msg)
}
