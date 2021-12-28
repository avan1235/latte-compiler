package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span

sealed class Type(private val name: String, val bytes: Int) : AstNode {
  override val span: Span? = null
  override fun toString(): String = name
}

object IntType : Type("int", bytes = 4)
object StringType : Type("string", bytes = 4)
object BooleanType : Type("boolean", bytes = 1)
object VoidType : Type("void", bytes = 0)
