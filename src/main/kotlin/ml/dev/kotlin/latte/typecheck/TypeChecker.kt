package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.TypeCheckException

fun ProgramNode.typeCheck(): TypeCheckedProgram = TypeChecker().run { this@typeCheck.typeCheck() }

data class TypeCheckedProgram(val program: ProgramNode)

private class TypeChecker(
  private val funEnv: MutableMap<String, Type> = STD_LIB_FUNCTIONS.toMutableMap(),
  private val hierarchy: ClassHierarchy = ClassHierarchy(),
  private val varEnv: StackTable<String, Type> = StackTable(),
  private val thisFields: MutableMap<String, Type> = HashMap(),
  private val thisMethods: MutableMap<String, Type> = HashMap(),
  private var expectedReturnType: Type? = null,
) {

  fun ProgramNode.typeCheck(): TypeCheckedProgram {
    val functions = topDefs.filterIsInstance<FunDefNode>()
    val classes = topDefs.filterIsInstance<ClassDefNode>()

    functions.forEach { it.addToFunEnv() }
    classes.forEach { hierarchy.addClass(it) }

    hierarchy.buildClassStructure()
    functions.forEach { it.typeCheck() }
    classes.forEach { classDef -> with(classDef) { methods.forEach { it.typeCheck(ident, parentClass) } } }

    if (ENTRY_LABEL !in funEnv) err("No main function defined")
    return TypeCheckedProgram(this)
  }

  private fun FunDefNode.addToFunEnv() {
    val name = ident mangled args.list.map { it.type }
    if (name in funEnv) err("Redefined function $ident")
    funEnv[name] = type
    mangledName = name
  }

  private fun FunDefNode.typeCheck(inClass: String? = null, parentClass: String? = null): Unit = varEnv.onLevel {
    expectReturn(type, ident, addVoidRetTo = block) {
      thisFields.apply { clear() }
      thisMethods.apply { clear() }
      inClass?.let {
        thisFields.putAll(hierarchy.classFields(it))
        thisMethods.putAll(hierarchy.classMethods(it))
      }
      verifyReturnTypeMatchesOverride(inClass, parentClass)
      args.list.forEach { args.addToVarEnv(it.type, it.ident) }
      block.typeCheck()
    }
  }

  private fun FunDefNode.verifyReturnTypeMatchesOverride(inClass: String?, parentClass: String?) {
    if (inClass == null) return
    if (parentClass == null) return
    val thisReturns = thisMethods[mangledName] ?: err("Not defined function $ident which should be available")
    val parentReturns = hierarchy.classMethods(parentClass)[mangledName] ?: return
    if (hierarchy.isSubType(thisReturns, parentReturns)) return
    err("Return type of $ident doesn't match overridden function return type")
  }

  private fun AstNode.expectReturn(type: Type, ident: String, addVoidRetTo: BlockNode, onAction: () -> LastReturnType) {
    expectedReturnType = type
    val last = onAction()
    when {
      last == null && type == VoidType -> addVoidRetTo.stmts += VRetStmtNode()
      last != null && hierarchy.isSubType(last, type) -> Unit
      else -> err("Expected $ident to return $type")
    }
    expectedReturnType = null
  }

  private fun BlockNode.typeCheck(): LastReturnType {
    var last: LastReturnType = null
    stmts.forEach { last = it.typeCheck() ?: last }
    return last
  }

  private fun StmtNode.typeCheck(): LastReturnType = when (this) {
    EmptyStmtNode -> noReturn()
    is ExprStmtNode -> noReturn { expr.type() }
    is BlockStmtNode -> varEnv.onLevel { block.typeCheck() }
    is AssStmtNode -> noReturn { typeCheckAss(this) }
    is DeclStmtNode -> noReturn { items.forEach { typeCheckDecl(it, type) } }
    is DecrStmtNode -> noReturn { typeCheckUnOp(ident, IntType) }
    is IncrStmtNode -> noReturn { typeCheckUnOp(ident, IntType) }
    is WhileStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is CondStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is CondElseStmtNode -> typeCheckCondElse(expr, onTrue, onFalse)
    is VRetStmtNode -> typeCheckReturn(VoidType)
    is RetStmtNode -> typeCheckReturn(expr.type())
    is RefAssStmtNode -> TODO()
  }

  private fun typeCheckAss(assStmt: AssStmtNode) {
    val varType = varEnv[assStmt.ident] ?: assStmt.err("Cannot assign value to not declared variable")
    val exprType = assStmt.expr.type()
    if (varType != exprType) assStmt.err("Cannot assign value of type $exprType to variable of type $varType")
  }

  private fun typeCheckDecl(item: ItemNode, type: Type) {
    item.addToVarEnv(type, item.ident)
    when (item) {
      is InitItemNode -> {
        val exprType = item.expr.type()
        if (exprType != type) item.err("Cannot assign value of type $exprType to variable of type $type")
      }
      is NotInitItemNode -> Unit
    }
  }

  private fun AstNode.typeCheckUnOp(name: String, type: Type): LastReturnType =
    if (varEnv[name] == type) null else err("Expected $type for operation")

  private fun AstNode.typeCheckReturn(type: Type): LastReturnType =
    if (expectedReturnType == type) type else err("Expected to return $expectedReturnType but got $type")

  private fun typeCheckCondElse(expr: ExprNode, onTrue: StmtNode, onFalse: StmtNode): LastReturnType {
    val checkType = expr.type()
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    val onTrueRet = varEnv.onLevel { onTrue.typeCheck() }
    val onFalseRet = varEnv.onLevel { onFalse.typeCheck() }
    return if (onTrueRet == onFalseRet) onTrueRet else null
  }

  private fun typeCheckCond(expr: ExprNode, stmt: StmtNode) {
    val checkType = expr.type()
    if (checkType != BooleanType) expr.err("Expected condition to have $BooleanType type but found $checkType")
    varEnv.onLevel { stmt.typeCheck() }
  }

  private fun AstNode.addToVarEnv(type: Type, name: String) {
    if (type == VoidType) err("Cannot declare variable with void type")
    if (name in varEnv.currentLevelNames) err("Redeclared variable for name $name")
    varEnv[name] = type
  }

  private fun ExprNode.type(): Type = when (this) {
    is BinOpExprNode -> {
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
    is BoolExprNode -> BooleanType
    is FunCallExprNode -> {
      val argsTypes = args.map { it.type() }
      val name = name mangled argsTypes
      mangledName = name
      funEnv[name] ?: err("Not defined function ${this.name}$argsTypes")
    }
    is IdentExprNode -> varEnv[value] ?: err("Not defined variable with name $value")
    is IntExprNode -> IntType
    is StringExprNode -> StringType
    is UnOpExprNode -> expr.type().let { type ->
      when {
        op == UnOp.NEG && type == IntType -> type
        op == UnOp.NOT && type == BooleanType -> type
        else -> err("Invalid unary operation types")
      }
    }
    is FieldExprNode -> TODO("Handle field expressions for classes")
    is ConstructorCallExprNode -> TODO("Handle constructor calls for classes")
    is MethodCallExprNode -> TODO()
    is CastExprNode -> TODO()
    is NullExprNode -> TODO()
    is ThisExprNode -> TODO()
  }
}

private typealias LastReturnType = Type?

private fun AstNode.err(message: String): Nothing =
  throw TypeCheckException(LocalizedMessage(message, span?.from))

private inline fun noReturn(action: () -> Unit = {}): LastReturnType {
  action()
  return null
}
