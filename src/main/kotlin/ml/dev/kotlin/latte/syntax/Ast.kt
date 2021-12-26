package ml.dev.kotlin.latte.syntax

data class Program(val topDefs: List<TopDef>)

sealed interface Type
object IntType : Type
object StringType : Type
object BooleanType : Type
object VoidType : Type

sealed interface BinOp
enum class NumOp : BinOp { Plus, Minus, Times, Divide, Mod }
enum class RelOp : BinOp { LT, LE, GT, GE, EQ, NE }
enum class BooleanOp : BinOp { And, Or }

data class TopDef(
    val type: Type,
    val ident: Ident,
    val args: List<Arg>,
    val block: Block,
)

data class Arg(val type: Type, val ident: Ident)
data class Block(val stmts: List<Stmt>)

sealed interface Item
data class NotInit(val ident: Ident) : Item
data class Init(val ident: Ident, val expr: Expr) : Item

sealed interface Stmt
data class BlockStmt(val block: Block) : Stmt
data class Decl(val type: Type, val items: List<Item>) : Stmt
data class Ass(val ident: Ident, val expr: Expr) : Stmt
data class Incr(val ident: Ident) : Stmt
data class Decr(val ident: Ident) : Stmt
data class Ret(val expr: Expr) : Stmt
object VRet : Stmt
data class Cond(val expr: Expr, val onTrue: Stmt) : Stmt
data class CondElse(val expr: Expr, val onTrue: Stmt, val onFalse: Stmt) : Stmt
data class While(val expr: Expr, val onTrue: Stmt) : Stmt
data class ExprStmt(val expr: Expr) : Stmt

sealed interface Expr
data class BinOpExpr(val left: Expr, val opExpr: BinOp, val right: Expr) : Expr
data class IdentExpr(val ident: Ident) : Expr
data class IntExpr(val digits: String) : Expr
data class BoolExpr(val value: Boolean) : Expr
data class StringExpr(val value: String) : Expr
data class FunCallExpr(val name: Ident, val args: List<Expr>) : Expr

typealias Ident = String