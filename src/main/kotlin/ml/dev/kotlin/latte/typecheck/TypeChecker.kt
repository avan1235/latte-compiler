package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.TypeCheckException

fun Program.typeCheck(): TypeCheckedProgram = TypeChecker().run { this@typeCheck.typeCheck() }

@JvmInline
value class TypeCheckedProgram(val program: Program)

private data class TypeChecker(
  private val funEnv: MutableMap<String, Type> = STD_LIB_FUNCTIONS.toMutableMap(),
  private val varEnv: StackTable<String, Type> = StackTable(),
  private var expectedReturnType: Type? = null,
) {

  fun Program.typeCheck(): TypeCheckedProgram {
    topDefs.onEach { if (it is FunDef) it.addToFunEnv() }.forEach { if (it is FunDef) it.typeCheck() }
    if (ENTRY_LABEL !in funEnv) err("No main function defined")
    return TypeCheckedProgram(this)
  }

  private fun FunDef.addToFunEnv() {
    val name = ident mangled args.list.map { it.type }
    if (name in funEnv) err("Redefined function $ident")
    funEnv[name] = type
    mangledName = name
  }

  private fun FunDef.typeCheck(): Unit = varEnv.onLevel {
    expectedReturnType = type
    args.list.forEach { args.addToVarEnv(it.type, it.ident) }
    val last = block.typeCheck()
    if (last != type && type != VoidType) err("Expected $ident to return $type")
    if (last == null && type == VoidType) block.stmts += VRetStmt()
    expectedReturnType = null
  }

  private fun Block.typeCheck(): LastReturnType {
    var last: LastReturnType = null
    stmts.forEach { last = it.typeCheck() ?: last }
    return last
  }

  private fun Stmt.typeCheck(): LastReturnType = when (this) {
    EmptyStmt -> noReturn()
    is ExprStmt -> noReturn { expr.type() }
    is BlockStmt -> varEnv.onLevel { block.typeCheck() }
    is AssStmt -> noReturn { typeCheckAss(this) }
    is DeclStmt -> noReturn { items.forEach { typeCheckDecl(it, type) } }
    is DecrStmt -> noReturn { typeCheckUnOp(ident, IntType) }
    is IncrStmt -> noReturn { typeCheckUnOp(ident, IntType) }
    is WhileStmt -> noReturn { typeCheckCond(expr, onTrue) }
    is CondStmt -> noReturn { typeCheckCond(expr, onTrue) }
    is CondElseStmt -> typeCheckCondElse(expr, onTrue, onFalse)
    is VRetStmt -> typeCheckReturn(VoidType)
    is RetStmt -> typeCheckReturn(expr.type())
    is RefAssStmt -> TODO()
  }

  private fun typeCheckAss(assStmt: AssStmt) {
    val varType = varEnv[assStmt.ident] ?: assStmt.err("Cannot assign value to not declared variable")
    val exprType = assStmt.expr.type()
    if (varType != exprType) assStmt.err("Cannot assign value of type $exprType to variable of type $varType")
  }

  private fun typeCheckDecl(item: Item, type: Type) {
    item.addToVarEnv(type, item.ident)
    when (item) {
      is InitItem -> {
        val exprType = item.expr.type()
        if (exprType != type) item.err("Cannot assign value of type $exprType to variable of type $type")
      }
      is NotInitItem -> Unit
    }
  }

  private fun AstNode.typeCheckUnOp(name: String, type: Type): LastReturnType =
    if (varEnv[name] == type) null else err("Expected $type for operation")

  private fun AstNode.typeCheckReturn(type: Type): LastReturnType =
    if (expectedReturnType == type) type else err("Expected to return $expectedReturnType but got $type")

  private fun typeCheckCondElse(expr: Expr, onTrue: Stmt, onFalse: Stmt): LastReturnType {
    val checkType = expr.type()
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    val onTrueRet = varEnv.onLevel { onTrue.typeCheck() }
    val onFalseRet = varEnv.onLevel { onFalse.typeCheck() }
    return if (onTrueRet == onFalseRet) onTrueRet else null
  }

  private fun typeCheckCond(expr: Expr, stmt: Stmt) {
    val checkType = expr.type()
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    varEnv.onLevel { stmt.typeCheck() }
  }

  private fun AstNode.addToVarEnv(type: Type, name: String) {
    if (type == VoidType) err("Cannot declare variable with void type")
    if (name in varEnv.currentLevelNames) err("Redeclared variable for name $name")
    varEnv[name] = type
  }

  private fun Expr.type(): Type = when (this) {
    is BinOpExpr -> {
      val left = left.type()
      val right = right.type()
      if (left != right) err("Cannot define binary operation on $left and $right")
      fun bothOf(vararg types: Type) = if (left !in types || right !in types)
        err(
          "Cannot define binary operation on $left and $right, " +
            "as expected them to have one of types: ${types.joinToString(", ")}"
        ) else Unit

      when (op) {
        NumOp.PLUS -> bothOf(IntType, StringType).let { left }
        is BooleanOp -> bothOf(BooleanType).let { BooleanType }
        is NumOp -> bothOf(IntType).let { left }
        is RelOp -> bothOf(IntType, BooleanType).let { BooleanType }
      }
    }
    is BoolExpr -> BooleanType
    is FunCallExpr -> {
      val argsTypes = args.map { it.type() }
      val name = name mangled argsTypes
      mangledName = name
      funEnv[name] ?: err("Not defined function ${this.name}$argsTypes")
    }
    is IdentExpr -> varEnv[value] ?: err("Not defined variable with name $value")
    is IntExpr -> IntType
    is StringExpr -> StringType
    is UnOpExpr -> expr.type().let { type ->
      when {
        op == UnOp.NEG && type == IntType -> type
        op == UnOp.NOT && type == BooleanType -> type
        else -> err("Invalid unary operation types")
      }
    }
    is FieldExpr -> TODO("Handle field expressions for classes")
    is ConstructorCallExpr -> TODO("Handle constructor calls for classes")
    is MethodCallExpr -> TODO()
    is CastExpr -> TODO()
    is NullExpr -> TODO()
  }
}

private typealias LastReturnType = Type?

private fun AstNode.err(message: String): Nothing =
  throw TypeCheckException(LocalizedMessage(message, span?.from))

private inline fun noReturn(action: () -> Unit = {}): LastReturnType {
  action()
  return null
}
