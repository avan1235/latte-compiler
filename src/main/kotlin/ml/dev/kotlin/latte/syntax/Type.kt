package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.LatteIllegalStateException
import ml.dev.kotlin.latte.util.Span
import ml.dev.kotlin.latte.util.msg

sealed interface Type : AstNode {
  val typeName: String
  val size: Bytes
  override fun toString(): String
}

typealias Bytes = Int

enum class PrimitiveType(override val typeName: String, override val size: Bytes) : Type {
  IntType("int", 4),
  StringType("string", 4),
  BooleanType("boolean", 4),
  VoidRefType("void~", 4),
  VoidType("void", 4) {
    override val size: Bytes get() = throw LatteIllegalStateException("Cannot get size of $this type".msg)
  };

  override val span: Span? = null
  override fun toString(): String = typeName
}

class RefType(override val typeName: String, override val span: Span? = null) : Type {
  override val size: Bytes = 4
  override fun toString(): String = "$typeName~"
  override fun hashCode(): Int = typeName.hashCode()
  override fun equals(other: Any?): Boolean = (other as? RefType)?.typeName == typeName
}
