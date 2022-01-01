package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span
import kotlin.properties.Delegates.notNull

sealed interface AstNode {
  val span: Span?
}

data class Program(val topDefs: List<TopDef>, override val span: Span? = null) : AstNode

sealed interface TopDef : AstNode

data class ClassField(val type: Type, val ident: String, override val span: Span? = null) : AstNode

data class ClassDef(
  val ident: String,
  val fields: List<ClassField>,
  val methods: List<MethodDef>,
  val parentClass: String? = null,
  override val span: Span? = null,
) : TopDef

data class MethodDef(
  val type: Type,
  val ident: String,
  val args: Args,
  val block: Block,
  override val span: Span? = null
) : AstNode {
  var mangledName by notNull<String>()
}

data class FunDef(
  val type: Type,
  val ident: String,
  val args: Args,
  val block: Block,
  override val span: Span? = null
) : TopDef {
  var mangledName by notNull<String>()
}

data class Args(val list: List<Arg>, override val span: Span? = null) : AstNode
data class Arg(val type: Type, val ident: String)
data class Block(val stmts: List<Stmt>, override val span: Span? = null) : AstNode

sealed interface Item : AstNode {
  val ident: String
}

data class NotInitItem(override val ident: String, override val span: Span? = null) : Item
data class InitItem(override val ident: String, val expr: Expr, override val span: Span? = null) : Item

sealed interface Stmt : AstNode
data class BlockStmt(val block: Block, override val span: Span? = null) : Stmt
data class DeclStmt(val type: Type, val items: List<Item>, override val span: Span? = null) : Stmt
data class AssStmt(val ident: String, val expr: Expr, override val span: Span? = null) : Stmt
data class RefAssStmt(val to: Expr, val field: String, val expr: Expr, override val span: Span? = null) : Stmt
data class IncrStmt(val ident: String, override val span: Span? = null) : Stmt
data class DecrStmt(val ident: String, override val span: Span? = null) : Stmt
data class RetStmt(val expr: Expr, override val span: Span? = null) : Stmt
data class VRetStmt(override val span: Span? = null) : Stmt
data class CondStmt(val expr: Expr, val onTrue: Stmt, override val span: Span? = null) : Stmt
data class CondElseStmt(val expr: Expr, val onTrue: Stmt, val onFalse: Stmt, override val span: Span? = null) : Stmt
data class WhileStmt(val expr: Expr, val onTrue: Stmt, override val span: Span? = null) : Stmt
data class ExprStmt(val expr: Expr, override val span: Span? = null) : Stmt
object EmptyStmt : Stmt {
  override val span: Span? = null
}

sealed interface Expr : AstNode
data class NullExpr(override val span: Span? = null) : Expr
data class UnOpExpr(val op: UnOp, val expr: Expr, override val span: Span? = null) : Expr
data class BinOpExpr(val left: Expr, val op: BinOp, val right: Expr, override val span: Span? = null) : Expr
data class FieldExpr(val expr: Expr, val value: String, override val span: Span? = null) : Expr
data class IdentExpr(val value: String, override val span: Span? = null) : Expr
data class IntExpr(val value: String, override val span: Span? = null) : Expr
data class BoolExpr(val value: Boolean, override val span: Span? = null) : Expr
data class StringExpr(val value: String, override val span: Span? = null) : Expr
data class ConstructorCallExpr(val type: Type, override val span: Span? = null) : Expr
data class CastExpr(val type: Type, val casted: Expr, override val span: Span? = null) : Expr
data class FunCallExpr(val name: String, val args: List<Expr>, override val span: Span? = null) : Expr {
  var mangledName by notNull<String>()
}

data class MethodCallExpr(val self: Expr, val name: String, val args: List<Expr>, override val span: Span? = null) :
  Expr {
  var mangledName by notNull<String>()
}

