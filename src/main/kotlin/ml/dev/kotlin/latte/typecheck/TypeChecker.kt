package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.ExceptionLocalizedMessage
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.TypeCheckException

fun Program.typeCheck() = TypeChecker().typeCheck(this)

private data class TypeChecker(
  private val funEnv: MutableMap<String, Type> = stdLibFunctionTypes(),
  private val varEnv: StackTable<String, Type> = StackTable(),
  private var expectedReturnType: Type? = null,
) {

  fun typeCheck(program: Program) {
    program.topDefs.onEach { addToFunEnv(it) }.forEach { typeCheck(it) }
  }

  private fun addToFunEnv(topDef: TopDef) {
    val name = topDef.ident mangled topDef.args.list.map { it.type }
    if (name in funEnv) topDef.err("Redefined function ${topDef.ident}")
    funEnv[name] = topDef.type
    topDef.mangledName = name
  }

  private fun typeCheck(topDef: TopDef) = varEnv.level {
    expectedReturnType = topDef.type
    topDef.args.list.forEach { topDef.args.addToVarEnv(it.type, it.ident) }
    val last = typeCheck(topDef.block)
    if (last != topDef.type && topDef.type != VoidType) topDef.err("Expected ${topDef.ident} to return ${topDef.type}")
    expectedReturnType = null
  }

  private fun typeCheck(block: Block): LastReturnType {
    var last: LastReturnType = null
    block.stmts.forEach { last = typeCheck(it) ?: last }
    return last
  }

  private fun typeCheck(stmt: Stmt): LastReturnType = when (stmt) {
    EmptyStmt -> noReturn()
    is ExprStmt -> noReturn { typeOf(stmt.expr) }
    is BlockStmt -> varEnv.level { typeCheck(stmt.block) }
    is AssStmt -> noReturn { typeCheck(stmt) }
    is DeclStmt -> noReturn { stmt.items.forEach { typeCheck(it, stmt.type) } }
    is DecrStmt -> noReturn { stmt.typeCheckUnOp(stmt.ident, IntType) }
    is IncrStmt -> noReturn { stmt.typeCheckUnOp(stmt.ident, IntType) }
    is WhileStmt -> noReturn { typeCheckCond(stmt.expr, stmt.onTrue) }
    is CondStmt -> noReturn { typeCheckCond(stmt.expr, stmt.onTrue) }
    is CondElseStmt -> typeCheckCondElse(stmt.expr, stmt.onTrue, stmt.onFalse)
    is VRetStmt -> stmt.typeCheckReturn(VoidType)
    is RetStmt -> stmt.typeCheckReturn(typeOf(stmt.expr))
  }

  private fun typeCheck(assStmt: AssStmt) {
    val varType = varEnv[assStmt.ident] ?: assStmt.err("Cannot assign value to not declared variable")
    val exprType = typeOf(assStmt.expr)
    if (varType != exprType) assStmt.err("Cannot assign value of type $exprType to variable of type $varType")
  }

  private fun typeCheck(item: Item, type: Type) {
    item.addToVarEnv(type, item.ident)
    when (item) {
      is InitItem -> {
        val exprType = typeOf(item.expr)
        if (exprType != type) item.err("Cannot assign value of type $exprType to variable of type $type")
      }
      is NotInitItem -> Unit
    }
  }

  private fun AstNode.typeCheckUnOp(name: String, type: Type): LastReturnType =
    if (varEnv[name] == type) null else err("Expected $type for operation")

  private fun AstNode.typeCheckReturn(type: Type): LastReturnType =
    if (expectedReturnType != type) err("Expected to return $type") else type

  private fun typeCheckCondElse(expr: Expr, onTrue: Stmt, onFalse: Stmt): LastReturnType {
    val checkType = typeOf(expr)
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    val onTrueRet = typeCheck(onTrue)
    val onFalseRet = typeCheck(onFalse)
    return if (onTrueRet == onFalseRet) onTrueRet else null
  }

  private fun typeCheckCond(expr: Expr, stmt: Stmt) {
    val checkType = typeOf(expr)
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    typeCheck(stmt)
  }

  private fun AstNode.addToVarEnv(type: Type, name: String) {
    if (name in varEnv.currentLevelNames) err("Redeclared variable for name $name")
    varEnv[name] = type
  }

  private fun typeOf(expr: Expr): Type = when (expr) {
    is BinOpExpr -> {
      val left = typeOf(expr.left)
      val right = typeOf(expr.right)
      if (left != right) expr.err("Cannot define binary operation on $left and $right")
      fun bothOf(vararg types: Type) = if (left !in types || right !in types)
        expr.err(
          "Cannot define binary operation on $left and $right, " +
            "as expected them to have one of types: ${types.joinToString(", ")}"
        ) else Unit

      when (expr.opExpr) {
        NumOp.Plus -> bothOf(IntType, StringType).let { left }
        is BooleanOp -> bothOf(BooleanType).let { BooleanType }
        is NumOp -> bothOf(IntType).let { left }
        is RelOp -> bothOf(IntType, BooleanType).let { BooleanType }
      }
    }
    is BoolExpr -> BooleanType
    is FunCallExpr -> {
      val argsTypes = expr.args.map { typeOf(it) }
      val name = expr.name mangled argsTypes
      expr.mangledName = name
      funEnv[name] ?: expr.err("Not defined function ${expr.name}$argsTypes")
    }
    is IdentExpr -> varEnv[expr.text] ?: expr.err("Not defined variable with name ${expr.text}")
    is IntExpr -> IntType
    is StringExpr -> StringType
    is UnOpExpr -> typeOf(expr.expr).let { type ->
      when {
        expr.op == UnOp.Neg && type == IntType -> type
        expr.op == UnOp.Not && type == BooleanType -> type
        else -> expr.err("Invalid unary operation types")
      }
    }
  }
}

private typealias LastReturnType = Type?

private fun stdLibFunctionTypes() = HashMap<String, Type>().apply {
  put("printInt", VoidType)
  put("printString", VoidType)
  put("error", VoidType)
  put("readInt", IntType)
  put("readString", StringType)
}

private fun AstNode.err(message: String): Nothing =
  throw TypeCheckException(ExceptionLocalizedMessage(message, span?.from))

private inline fun noReturn(action: () -> Unit = {}): LastReturnType {
  action()
  return null
}
