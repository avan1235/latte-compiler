package ml.dev.kotlin.latte.syntax

sealed interface AstNode
data class Program(val topDefs: List<TopDef>) : AstNode

sealed interface Type : AstNode
object IntType : Type
object StringType : Type
object BooleanType : Type
object VoidType : Type

enum class UnOp : AstNode { Not, Neg }
sealed interface BinOp : AstNode
enum class NumOp : BinOp { Plus, Minus, Times, Divide, Mod }
enum class RelOp : BinOp { LT, LE, GT, GE, EQ, NE }
enum class BooleanOp : BinOp { And, Or }

data class TopDef(val type: Type, val ident: SpannedText, val args: Args, val block: Block) : AstNode

data class Args(val args: List<Arg>) : AstNode
data class Arg(val type: Type, val ident: SpannedText) : AstNode
data class Block(val stmts: List<Stmt>) : AstNode

sealed interface Item : AstNode
data class NotInitItem(val ident: SpannedText) : Item
data class InitItem(val ident: SpannedText, val expr: Expr) : Item

sealed interface Stmt : AstNode
data class BlockStmt(val block: Block) : Stmt
data class DeclStmt(val type: Type, val items: List<Item>) : Stmt
data class AssStmt(val ident: SpannedText, val expr: Expr) : Stmt
data class IncrStmt(val ident: SpannedText) : Stmt
data class DecrStmt(val ident: SpannedText) : Stmt
data class RetStmt(val expr: Expr) : Stmt
object VRetStmt : Stmt
object EmptyStmt : Stmt
data class CondStmt(val expr: Expr, val onTrue: Stmt) : Stmt
data class CondElseStmt(val expr: Expr, val onTrue: Stmt, val onFalse: Stmt) : Stmt
data class WhileStmt(val expr: Expr, val onTrue: Stmt) : Stmt
data class ExprStmt(val expr: Expr) : Stmt

sealed interface Expr : AstNode
data class UnOpExpr(val op: UnOp, val expr: Expr) : Expr
data class BinOpExpr(val left: Expr, val opExpr: BinOp, val right: Expr) : Expr
data class IdentExpr(val ident: SpannedText) : Expr
data class IntExpr(val node: SpannedText) : Expr
data class BoolExpr(val value: Boolean) : Expr
data class StringExpr(val node: String) : Expr
data class FunCallExpr(val name: SpannedText, val args: List<Expr>) : Expr

data class SpannedText(val text: String, val row: Int, val startIndex: Int, val stopIndex: Int)