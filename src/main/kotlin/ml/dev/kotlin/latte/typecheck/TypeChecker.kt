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
  private val thisMethods: MutableMap<String, Type> = HashMap(),
  private val thisFields: MutableMap<String, Type> = HashMap(),
  private var thisClass: String? = null,
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
      thisClass = inClass
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

  private fun FunDefNode.verifyReturnTypeMatchesOverride(inClass: String?, parent: String?): Unit = with(hierarchy) {
    if (inClass == null) return
    if (parent == null) return
    val thisReturns = thisMethods[mangledName] ?: err("Not defined function $ident which should be available")
    val parentReturns = hierarchy.classMethods(parent)[mangledName] ?: return
    if (thisReturns isSubTypeOf parentReturns) return
    err("Return type of $ident doesn't match overridden function return type")
  }

  private fun AstNode.expectReturn(
    type: Type,
    ident: String,
    addVoidRetTo: BlockNode,
    onAction: () -> LastReturnType
  ): Unit = with(hierarchy) {
    expectedReturnType = type
    val last = onAction()
    when {
      last == null && type == VoidType -> addVoidRetTo.stmts += VRetStmtNode()
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
    is AssStmtNode -> noReturn { typeCheckAss(this) }
    is DeclStmtNode -> noReturn { items.forEach { typeCheckDecl(it, type) } }
    is DecrStmtNode -> noReturn { typeCheckUnOp(ident) }
    is IncrStmtNode -> noReturn { typeCheckUnOp(ident) }
    is WhileStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is CondStmtNode -> noReturn { typeCheckCond(expr, onTrue) }
    is RefAssStmtNode -> noReturn { typeCheckRefAss(this) }
    is CondElseStmtNode -> typeCheckCondElse(expr, onTrue, onFalse)
    is VRetStmtNode -> typeCheckReturn(VoidType)
    is RetStmtNode -> typeCheckReturn(expr.type())
  }

  private fun typeCheckRefAss(assStmt: RefAssStmtNode): Unit = with(assStmt) {
    val exprType = expr.type()
    val fieldName = fieldName
    val fieldType = hierarchy.classFields(to.type().typeName)[fieldName]
      ?: err("Cannot assign value to not existing field $fieldName")
    if (!with(hierarchy) { exprType isSubTypeOf fieldType })
      err("Cannot assign value of type $exprType to field of type $fieldType")
  }

  private fun typeCheckAss(assStmt: AssStmtNode): Unit = with(assStmt) {
    val varType = getVarType(ident) ?: err("Cannot assign value to not declared variable")
    val exprType = expr.type()
    if (!with(hierarchy) { exprType isSubTypeOf varType })
      err("Cannot assign value of type $exprType to variable of type $varType")
  }

  private fun typeCheckDecl(item: ItemNode, type: Type): Unit = with(item) {
    addToVarEnv(type, item.ident)
    when (this) {
      is InitItemNode -> {
        val exprType = expr.type()
        if (!with(hierarchy) { exprType isSubTypeOf type })
          err("Cannot assign value of type $exprType to variable of type $type")
      }
      is NotInitItemNode -> Unit
    }
  }

  private fun AstNode.typeCheckUnOp(ident: String): LastReturnType =
    if (getVarType(ident) == IntType) null else err("Expected $IntType for operation")

  private fun AstNode.typeCheckReturn(type: Type): LastReturnType = with(hierarchy) {
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

  private fun getVarType(ident: String): Type? = varEnv[ident] ?: thisClass?.let { hierarchy.classFields(it)[ident] }

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
          if (left is ClassType && right is ClassType) BooleanType
          else bothOf(IntType, BooleanType).let { BooleanType }
      }
    }
    is BoolExprNode -> BooleanType
    is FunCallExprNode -> {
      val argsTypes = args.map { it.type() }
      val name = name mangled argsTypes
      mangledName = name
      funEnv[name] ?: err("Not defined function ${this@type.name}$argsTypes")
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
      hierarchy.classFields(fieldOf.typeName)[fieldName] ?: err("Not defined field $fieldName for $fieldOf")
    }
    is ConstructorCallExprNode -> when (type) {
      NullType -> err("Cannot create new instance of null type")
      is PrimitiveType -> err("Cannot create new instance of primitive type")
      is ClassType -> if (hierarchy.isTypeDefined(type)) type
      else err("Cannot create new instance of not defined type $type")
    }
    is MethodCallExprNode -> {
      val selfType = self.type()
      if (selfType !is ClassType) err("Cannot call ${this.name} on $selfType")
      val argsTypes = args.map { it.type() }
      val name = name mangled argsTypes
      mangledName = name
      hierarchy.classMethods(selfType.typeName)[name]
        ?: err("Not defined method ${this.name}$argsTypes for $selfType")
    }
    is CastExprNode -> with(hierarchy) {
      val castTo = if (hierarchy.isTypeDefined(type)) type else err("Cannot cast to undefined type $type")
      when (val exprType = casted.type()) {
        is PrimitiveType -> if (castTo == exprType) castTo else err("Cannot cast $exprType to $castTo")
        is ClassType -> if (exprType isSubTypeOf castTo) castTo else err("Cannot cast $exprType to $castTo")
        NullType -> castTo
      }
    }
    is NullExprNode -> NullType
    is ThisExprNode -> ClassType(thisClass ?: err("Used 'this' expression with no class scope"))
  }
}

private typealias LastReturnType = Type?

private fun AstNode.err(message: String): Nothing =
  throw TypeCheckException(LocalizedMessage(message, span?.from))

private inline fun noReturn(action: () -> Unit = {}): LastReturnType {
  action()
  return null
}
