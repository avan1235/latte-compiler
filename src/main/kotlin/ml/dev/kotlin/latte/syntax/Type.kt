package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.LatteIllegalStateException
import ml.dev.kotlin.latte.util.Span
import ml.dev.kotlin.latte.util.msg

sealed class Type(private val name: String) : AstNode {
  override val span: Span? = null
  abstract val size: Bytes
  override fun toString(): String = name
}

typealias Bytes = Int

object IntType : Type("int") {
  override val size = 4
}

object StringType : Type("string") {
  override val size = 4
}

object BooleanType : Type("boolean") {
  override val size = 4
}

object VoidType : Type("void") {
  override val size get() = throw LatteIllegalStateException("Cannot get size of $this type".msg)
}

class RefType(name: String, override val span: Span) : Type(name) {
  override val size = 4
}
