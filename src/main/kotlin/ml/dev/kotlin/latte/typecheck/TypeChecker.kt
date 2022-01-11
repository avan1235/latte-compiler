package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.TypeCheckException
import ml.dev.kotlin.latte.util.unless

fun ProgramNode.typeCheck(): TypeCheckedProgram = TypeChecker().run { this@typeCheck.typeCheck() }

data class TypeCheckedProgram(val program: ProgramNode, val env: ClassHierarchy)

private class TypeChecker(
  private val env: ClassHierarchy = ClassHierarchy(),
  private val varEnv: StackTable<String, Type> = StackTable(),
  private var thisMethods: FunEnv? = null,
  private var thisFields: Map<String, Type>? = null,
  private var thisClass: String? = null,
  private var expectedReturnType: Type? = null,
) {

  fun ProgramNode.typeCheck(): TypeCheckedProgram {
    val functions = topDefs.filterIsInstance<FunDefNode>()
    val classes = topDefs.filterIsInstance<ClassDefNode>()

    functions.forEach { it.addToFunEnv() }
    classes.forEach { env.addClass(it) }

    env.buildClassStructure()
    functions.forEach { it.typeCheck() }
    classes.forEach { classDef -> with(classDef) { methods.forEach { it.typeCheck(ident, parentClass) } } }

    if (env[ENTRY_LABEL, NO_ARGS] == null) err("No main function defined")
    return TypeCheckedProgram(this, env)
  }

  private fun FunDefNode.addToFunEnv() {
    env += this
  }

  private fun FunDefNode.typeCheck(inClass: String? = null, parentClass: String? = null): Unit = varEnv.onLevel {
    expectReturn {
      thisClass = inClass
      thisFields = null
      thisMethods = null
      inClass?.let {
        thisFields = env.classFields(it)
        thisMethods = env.classMethods(it)
      }
      verifyOverrideReturnTypeMatchesParent(inClass, parentClass)
      args.list.forEach { args.addToVarEnv(it.type, it.ident) }
      block.typeCheck()
    }
  }

  private fun FunDefNode.verifyOverrideReturnTypeMatchesParent(inClass: String?, parent: String?) {
    if (inClass == null) return
    if (parent == null) return
    val argsTypes = args.list.map { it.type }
    val thisReturns = thisMethods?.get(ident, argsTypes)
      ?: err("Not defined function $ident which should be available")
    val parentReturns = env.classMethods(parent)[ident, argsTypes] ?: return
    if (thisReturns.ret isSubTypeOf parentReturns.ret) return
    err("Return type of $ident doesn't match overridden function return type")
  }

  private fun FunDefNode.expectReturn(onAction: () -> LastReturnType) {
    expectedReturnType = type
    val last = onAction()
    when {
      last == null && type == VoidType -> block.stmts += VRetStmtNode()
      last != null && last isSubTypeOf type -> Unit
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
    is DeclStmtNode -> noReturn { items.forEach { it.typeCheckDecl(type) } }
    is AssStmtNode -> noReturn { typeCheckAss() }
    is FieldAssStmtNode -> noReturn { typeCheckRefAss() }
    is UnOpModStmtNode -> noReturn { typeCheckUnOpMod() }
    is FieldUnOpModStmtNode -> noReturn { typeCheckRefUnOpMod() }
    is WhileStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is CondStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is CondElseStmtNode -> typeCheckCondElse(expr, onTrue, onFalse)
    is VRetStmtNode -> typeCheckReturn(VoidType)
    is RetStmtNode -> typeCheckReturn(expr.type())
  }

  private fun FieldAssStmtNode.typeCheckRefAss() {
    val exprType = expr.type()
    val fieldName = fieldName
    val fieldType = env.classFields(to.type().typeName)[fieldName]
      ?: err("Cannot assign value to not existing field $fieldName")
    unless(exprType isSubTypeOf fieldType) {
      err("Cannot assign value of type $exprType to field of type $fieldType")
    }
  }

  private fun AssStmtNode.typeCheckAss() {
    val varType = getVarType(ident) ?: err("Cannot assign value to not declared variable")
    val exprType = expr.type()
    unless(exprType isSubTypeOf varType) {
      err("Cannot assign value of type $exprType to variable of type $varType")
    }
  }

  private fun ItemNode.typeCheckDecl(type: Type) {
    addToVarEnv(type, ident)
    when (this) {
      is InitItemNode -> {
        val exprType = expr.type()
        unless(exprType isSubTypeOf type) {
          err("Cannot assign value of type $exprType to variable of type $type")
        }
      }
      is NotInitItemNode -> Unit
    }
  }

  private fun UnOpModStmtNode.typeCheckUnOpMod(): LastReturnType =
    if (getVarType(ident) == IntType) null else err("Expected $IntType for operation")

  private fun FieldUnOpModStmtNode.typeCheckRefUnOpMod(): LastReturnType {
    val checkType = expr.type()
    if (checkType !is RefType) err("Cannot modify field of not class type")
    val fieldType = env.classFields(checkType.typeName)[fieldName]
      ?: err("Modified not existing field $fieldName of ${checkType.typeName}")
    return if (fieldType == IntType) null else err("Expected $IntType for operation")
  }

  private fun AstNode.typeCheckReturn(type: Type): LastReturnType = with(env) {
    if (type isSubTypeOf expectedReturnType) type else err("Expected to return $expectedReturnType but got $type")
  }

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

  private fun getVarType(ident: String): Type? = varEnv[ident] ?: thisClass?.let { env.classFields(it)[ident] }

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
        is RelOp ->
          if (left is RefType && right is RefType) BooleanType
          else bothOf(IntType, BooleanType).let { BooleanType }
      }
    }
    is BoolExprNode -> BooleanType
    is FunCallExprNode -> {
      val argsTypes = args.map { it.type() }
      mangledName = name mangled argsTypes
      env[name, argsTypes]?.ret ?: err("Not defined function ${name}$argsTypes")
    }
    is IdentExprNode -> getVarType(value) ?: err("Not defined variable with name $value")
    is IntExprNode -> IntType
    is StringExprNode -> StringType
    is UnOpExprNode -> expr.type().let { type ->
      when {
        op == UnOp.NEG && type == IntType -> type
        op == UnOp.NOT && type == BooleanType -> type
        else -> err("Invalid types of unary operation")
      }
    }
    is FieldExprNode -> {
      val fieldOf = expr.type()
      env.classFields(fieldOf.typeName)[fieldName] ?: err("Not defined field $fieldName for $fieldOf")
    }
    is ConstructorCallExprNode -> when (type) {
      is RefType ->
        if (env.isTypeDefined(type)) type
        else err("Cannot create new instance of not defined type $type")
      is PrimitiveType -> err("Cannot create new instance of primitive type")
    }
    is ClassFunCallExprNode -> {
      val selfType = self.type()
      if (selfType !is RefType) err("Cannot call ${this.name} on $selfType")
      val argsTypes = args.map { it.type() }
      env.classMethods(selfType.typeName)[name, argsTypes]
        ?.also { mangledName = it.name }
        ?.ret ?: err("Not defined method ${this.name}$argsTypes for $selfType")
    }
    is CastExprNode -> {
      val castTo = if (env.isTypeDefined(type)) type else err("Cannot cast to undefined type $type")
      val exprType = casted.type()
      if (exprType isSubTypeOf castTo) castTo else err("Cannot cast $exprType to $castTo")
    }
    is NullExprNode -> VoidRefType
    is ThisExprNode -> RefType(thisClass ?: err("Used 'self' expression with no class scope"))
  }

  private infix fun Type.isSubTypeOf(other: Type): Boolean = with(env) { this@isSubTypeOf isSubTypeOf other }
}

private typealias LastReturnType = Type?

private fun AstNode.err(message: String): Nothing =
  throw TypeCheckException(LocalizedMessage(message, span?.from))

private inline fun noReturn(action: () -> Unit = {}): LastReturnType {
  action()
  return null
}
