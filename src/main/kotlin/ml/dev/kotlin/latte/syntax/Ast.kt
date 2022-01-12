package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.Span
import kotlin.properties.Delegates.notNull

sealed interface AstNode {
  val span: Span?
}

data class ProgramNode(val topDefs: List<TopDefNode>, override val span: Span? = null) : AstNode

sealed interface TopDefNode : AstNode

data class FieldNode(val type: Type, val ident: String, override val span: Span? = null) : AstNode

data class ClassDefNode(
  val ident: String,
  val fields: List<FieldNode>,
  val methods: List<FunDefNode>,
  val parentClass: String? = null,
  override val span: Span? = null,
) : TopDefNode

data class FunDefNode(
  val type: Type,
  val ident: String,
  val args: ArgsNode,
  val block: BlockNode,
  override val span: Span? = null
) : TopDefNode {
  var mangledName by notNull<String>()
}

data class ArgsNode(val list: List<ArgNode>, override val span: Span? = null) : AstNode
data class ArgNode(val type: Type, val ident: String)
data class BlockNode(val stmts: MutableList<StmtNode>, override val span: Span? = null) : AstNode

sealed interface ItemNode : AstNode {
  val ident: String
}

data class NotInitItemNode(override val ident: String, override val span: Span? = null) : ItemNode
data class InitItemNode(override val ident: String, val expr: ExprNode, override val span: Span? = null) : ItemNode

sealed interface StmtNode : AstNode
data class BlockStmtNode(val block: BlockNode, override val span: Span? = null) : StmtNode
data class DeclStmtNode(val type: Type, val items: List<ItemNode>, override val span: Span? = null) : StmtNode
data class RetStmtNode(val expr: ExprNode, override val span: Span? = null) : StmtNode
data class VRetStmtNode(override val span: Span? = null) : StmtNode
data class CondStmtNode(val expr: ExprNode, val onTrue: StmtNode, override val span: Span? = null) : StmtNode
data class CondElseStmtNode(
  val expr: ExprNode,
  val onTrue: StmtNode,
  val onFalse: StmtNode,
  override val span: Span? = null
) : StmtNode

data class AssStmtNode(
  val to: ExprNode?,
  val fieldName: String,
  val expr: ExprNode,
  override val span: Span? = null
) : StmtNode

data class UnOpModStmtNode(
  val to: ExprNode?,
  val fieldName: String,
  val op: UnOpMod,
  override val span: Span? = null
) : StmtNode

data class WhileStmtNode(val expr: ExprNode, val onTrue: StmtNode, override val span: Span? = null) : StmtNode
data class ExprStmtNode(val expr: ExprNode, override val span: Span? = null) : StmtNode
object EmptyStmtNode : StmtNode {
  override val span: Span? = null
}

sealed interface ExprNode : AstNode
data class NullExprNode(override val span: Span? = null) : ExprNode
data class ThisExprNode(override val span: Span? = null) : ExprNode
data class FieldExprNode(val expr: ExprNode, val fieldName: String, override val span: Span? = null) : ExprNode
data class IdentExprNode(val value: String, override val span: Span? = null) : ExprNode
data class IntExprNode(val value: String, override val span: Span? = null) : ExprNode
data class BoolExprNode(val value: Boolean, override val span: Span? = null) : ExprNode
data class StringExprNode(val value: String, override val span: Span? = null) : ExprNode
data class ConstructorCallExprNode(val type: Type, override val span: Span? = null) : ExprNode
data class CastExprNode(val type: Type, val casted: ExprNode, override val span: Span? = null) : ExprNode
data class UnOpExprNode(val op: UnOp, val expr: ExprNode, override val span: Span? = null) : ExprNode
data class BinOpExprNode(
  val left: ExprNode,
  val op: BinOp,
  val right: ExprNode,
  override val span: Span? = null
) : ExprNode

data class FunCallExprNode(
  val self: ExprNode?,
  val funName: String,
  val args: List<ExprNode>,
  override val span: Span? = null
) : ExprNode {
  var mangledName by notNull<String>()
}

