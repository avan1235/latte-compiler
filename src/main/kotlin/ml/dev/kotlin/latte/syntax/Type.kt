package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span

sealed class Type(private val name: String): AstNode {
  override val span: Span? = null
  override fun toString(): String = name
}
object IntType : Type("int")
object StringType : Type("string")
object BooleanType : Type("boolean")
object VoidType : Type("void")
