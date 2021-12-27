package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span
import kotlin.properties.Delegates.notNull

sealed interface AstNode {
  val span: Span?
}

data class Program(val topDefs: List<TopDef>, override val span: Span) : AstNode

data class TopDef(
  val type: Type,
  val ident: String,
  val args: Args,
  val block: Block,
  override val span: Span
) : AstNode {
  var mangledName by notNull<String>()
}

data class Args(val list: List<Arg>, override val span: Span?) : AstNode
data class Arg(val type: Type, val ident: String)
data class Block(val stmts: List<Stmt>, override val span: Span) : AstNode

sealed interface Item : AstNode {
  val ident: String
}

data class NotInitItem(override val ident: String, override val span: Span) : Item
data class InitItem(override val ident: String, val expr: Expr, override val span: Span) : Item

sealed interface Stmt : AstNode
data class BlockStmt(val block: Block, override val span: Span) : Stmt
data class DeclStmt(val type: Type, val items: List<Item>, override val span: Span) : Stmt
data class AssStmt(val ident: String, val expr: Expr, override val span: Span) : Stmt
data class IncrStmt(val ident: String, override val span: Span) : Stmt
data class DecrStmt(val ident: String, override val span: Span) : Stmt
data class RetStmt(val expr: Expr, override val span: Span) : Stmt
data class VRetStmt(override val span: Span) : Stmt
data class CondStmt(val expr: Expr, val onTrue: Stmt, override val span: Span) : Stmt
data class CondElseStmt(val expr: Expr, val onTrue: Stmt, val onFalse: Stmt, override val span: Span) : Stmt
data class WhileStmt(val expr: Expr, val onTrue: Stmt, override val span: Span) : Stmt
data class ExprStmt(val expr: Expr, override val span: Span) : Stmt
object EmptyStmt : Stmt {
  override val span: Span? = null
}

sealed interface Expr : AstNode

data class UnOpExpr(val op: UnOp, val expr: Expr, override val span: Span) : Expr
data class BinOpExpr(val left: Expr, val opExpr: BinOp, val right: Expr, override val span: Span) : Expr
data class IdentExpr(val text: String, override val span: Span) : Expr
data class IntExpr(val value: String, override val span: Span) : Expr
data class BoolExpr(val value: Boolean, override val span: Span) : Expr
data class StringExpr(val value: String, override val span: Span) : Expr
data class FunCallExpr(val name: String, val args: List<Expr>, override val span: Span) : Expr {
  var mangledName by notNull<String>()
}

