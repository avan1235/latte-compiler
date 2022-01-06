package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.quadruple.ControlFlowGraph.Companion.buildCFG
import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.typecheck.STD_LIB_FUNCTIONS
import ml.dev.kotlin.latte.typecheck.TypeCheckedProgram
import ml.dev.kotlin.latte.util.*

fun TypeCheckedProgram.toIR(): IR = IRGenerator().run { this@toIR.generate() }

data class IR(val graph: ControlFlowGraph, val strings: Map<String, Label>, val labelGenerator: () -> Label)

private data class IRGenerator(
  private val funEnv: MutableMap<String, Type> = STD_LIB_FUNCTIONS.toMutableMap(),
  private val quadruples: MutableList<Quadruple> = mutableListOf(),
  private val varEnv: StackTable<String, VirtualReg> = StackTable(),
  private val strings: MutableMap<String, Label> = hashMapOf("" to EMPTY_STRING_LABEL),
  private var labelIdx: Int = 0,
  private var emitting: Boolean = true,
) {
  fun TypeCheckedProgram.generate(): IR {
    program.topDefs.onEach { if (it is FunDefNode) it.addToFunEnv() }.forEach { if (it is FunDefNode) it.generate() }
    val labelGenerator = { freshLabel(prefix = "G") }
    val cfg = quadruples.buildCFG(labelGenerator)
    return IR(cfg, strings, labelGenerator)
  }

  private fun FunDefNode.addToFunEnv() {
    funEnv[mangledName] = type
  }

  private fun FunDefNode.generate() = varEnv.onLevel {
    var argOffset = 0
    val args = args.list.map { (type, name) ->
      addArg(name.label, type, argOffset).also { argOffset += type.size }
    }
    emit { FunCodeLabelQ(mangledName.label, args) }
    block.generate()
  }

  private fun BlockNode.generate(): Unit = stmts.forEach { it.generate() }

  private fun StmtNode.generate(): Unit = when (this) {
    EmptyStmtNode -> Unit
    is BlockStmtNode -> varEnv.onLevel { block.generate() }
    is DeclStmtNode -> items.forEach { it.generate(type) }
    is AssStmtNode -> emit { AssignQ(getVar(ident), expr.generate()) }
    is DecrStmtNode -> emit { getVar(ident).let { UnOpModQ(it, UnOpMod.DEC, it) } }
    is IncrStmtNode -> emit { getVar(ident).let { UnOpModQ(it, UnOpMod.INC, it) } }
    is ExprStmtNode -> expr.generate().unit()
    is RetStmtNode -> emit { RetQ(expr.generate()) }
    is VRetStmtNode -> emit { RetQ() }
    is CondStmtNode -> {
      val (trueLabel, endLabel) = get(count = 2) { freshLabel(prefix = "L") }
      generateCondElse(expr, trueLabel, endLabel)

      emit { CodeLabelQ(trueLabel) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(endLabel) }
    }
    is CondElseStmtNode -> {
      val (trueLabel, falseLabel, endLabel) = get(count = 3) { freshLabel(prefix = "L") }
      generateCondElse(expr, trueLabel, falseLabel)

      emit { CodeLabelQ(falseLabel) }
      varEnv.onLevel { onFalse.generate() }
      emit { JumpQ(endLabel) }

      emit { CodeLabelQ(trueLabel) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(endLabel) }
    }
    is WhileStmtNode -> {
      val (body, condition, endWhile) = get(count = 3) { freshLabel(prefix = "L") }
      emit { JumpQ(condition) }

      emit { CodeLabelQ(body) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(condition) }
      generateCondElse(expr, body, endWhile)

      emit { CodeLabelQ(endWhile) }
    }
    is RefAssStmtNode -> TODO()
  }

  private fun generateCondElse(expr: ExprNode, onTrue: Label, onFalse: Label): Unit {
    when {
      expr is UnOpExprNode && expr.op == UnOp.NOT -> generateCondElse(expr.expr, onFalse, onTrue)
      expr is BinOpExprNode && expr.op is RelOp -> {
        val lv = expr.left.generate()
        val rv = expr.right.generate()
        when {
          lv is VirtualReg && rv is VirtualReg && lv == rv && expr.op == RelOp.EQ -> emit { JumpQ(onTrue) }
          lv is VirtualReg && rv is VirtualReg && lv == rv && expr.op == RelOp.NE -> emit { JumpQ(onFalse) }
          lv is BooleanConstValue && rv is BooleanConstValue ->
            if (expr.op.rel(lv, rv).bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
          lv is IntConstValue && rv is IntConstValue ->
            if (expr.op.rel(lv, rv).bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
          else -> {
            emit { RelCondJumpQ(lv, expr.op, rv, onTrue) }
            emit { JumpQ(onFalse) }
          }
        }
      }
      expr is BinOpExprNode && expr.op == BooleanOp.AND -> {
        val midLabel = freshLabel(prefix = "M")
        generateCondElse(expr.left, midLabel, onFalse)
        emit { CodeLabelQ(midLabel) }
        generateCondElse(expr.right, onTrue, onFalse)
      }
      expr is BinOpExprNode && expr.op == BooleanOp.OR -> {
        val midLabel = freshLabel(prefix = "M")
        generateCondElse(expr.left, onTrue, midLabel)
        emit { CodeLabelQ(midLabel) }
        generateCondElse(expr.right, onTrue, onFalse)
      }
      else -> when (val cond = expr.generate()) {
        is BooleanConstValue -> if (cond.bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
        else -> {
          emit { CondJumpQ(cond, onTrue) }
          emit { JumpQ(onFalse) }
        }
      }
    }
  }

  private fun ItemNode.generate(type: Type): Unit = when (this) {
    is NotInitItemNode -> emit {
      val value = type.default
      AssignQ(addLocal(ident.label, type), value)
    }
    is InitItemNode -> emit {
      val value = expr.generate()
      AssignQ(addLocal(ident.label, type), value)
    }
  }

  private fun ExprNode.generate(): ValueHolder = when (this) {
    is FunCallExprNode -> freshTemp(getFunType(mangledName)) { to ->
      emit { FunCallQ(to, mangledName.label, args.map { it.generate() }) }
    }
    is IdentExprNode -> getVar(value)
    is BoolExprNode -> value.bool
    is IntExprNode -> value.int
    is StringExprNode -> addStringConst(value)
    is UnOpExprNode -> {
      val from = expr.generate()
      when (op) {
        UnOp.NEG ->
          if (from is IntConstValue) -from
          else freshTemp(IntType) { to -> emit { UnOpQ(to, op, from) } }
        UnOp.NOT ->
          if (from is BooleanConstValue) !from
          else freshTemp(BooleanType) { to -> emit { UnOpQ(to, op, from) } }
      }
    }
    is BinOpExprNode -> when (op) {
      BooleanOp.AND -> generateCondElse(left, BooleanOp.AND, right)
      BooleanOp.OR -> generateCondElse(left, BooleanOp.OR, right)
      else -> {
        val lv = left.generate()
        val rv = right.generate()
        when {
          lv is IntConstValue && rv is IntConstValue && op is RelOp -> op.rel(lv, rv)
          lv is IntConstValue && rv is IntConstValue && op is NumOp -> op.num(lv, rv)
          lv is StringConstValue && rv is StringConstValue -> addStringConst(lv.str + rv.str)
          lv is BooleanConstValue && rv is BooleanConstValue && op is RelOp -> op.rel(lv, rv)
          rv is IntConstValue && rv.int == 0 && (op == NumOp.PLUS || op == NumOp.MINUS) -> lv
          rv is IntConstValue && rv.int == 1 && (op == NumOp.TIMES || op == NumOp.DIVIDE) -> lv
          lv is IntConstValue && lv.int == 0 && op == NumOp.PLUS -> rv
          lv is IntConstValue && lv.int == 1 && op == NumOp.TIMES -> rv
          else -> when {
            op is NumOp && op == NumOp.PLUS && lv.type == StringType && rv.type == StringType ->
              freshTemp(StringType) { to -> emit { FunCallQ(to, "__concatString".label, listOf(lv, rv)) } }
            op is NumOp -> freshTemp(IntType) { to -> emit { BinOpQ(to, lv, op, rv) } }
            op is RelOp -> freshTemp(BooleanType) { to ->
              val falseLabel = freshLabel(prefix = "F")
              emit { AssignQ(to, false.bool) }
              emit { RelCondJumpQ(lv, op.rev, rv, falseLabel) }
              emit { AssignQ(to, true.bool) }
              emit { CodeLabelQ(falseLabel) }
            }
            else -> err("Unknown binary operation $this")
          }
        }
      }
    }
    is FieldExprNode -> TODO("Not implemented fields accessing")
    is ConstructorCallExprNode -> TODO("Not implemented constructor calls")
    is MethodCallExprNode -> TODO("Not implemented class method calls")
    is CastExprNode -> TODO()
    is NullExprNode -> TODO()
  }

  private fun generateCondElse(left: ExprNode, op: BooleanOp, right: ExprNode): LocalValue = freshTemp(BooleanType) { to ->
    CondElseStmtNode(
      BinOpExprNode(left, op, right),
      AssStmtNode(to.id, BoolExprNode(true)),
      AssStmtNode(to.id, BoolExprNode(false)),
    ).generate()
  }

  private inline fun emit(emit: () -> Quadruple) {
    if (emitting) quadruples += emit()
  }

  private fun ValueHolder.inMemory(): VirtualReg = when (this) {
    is IntConstValue -> freshTemp(IntType) { to -> emit { AssignQ(to, this) } }
    is StringConstValue -> freshTemp(StringType) { to -> emit { AssignQ(to, this) } }
    is BooleanConstValue -> freshTemp(BooleanType) { to -> emit { AssignQ(to, this) } }
    is VirtualReg -> this
  }

  private fun freshTemp(type: Type, action: (LocalValue) -> Unit = {}): LocalValue =
    LocalValue("@T${freshIdx()}", type).also { varEnv[it.id] = it }.also(action)

  private fun addStringConst(value: String): StringConstValue =
    StringConstValue(strings[value] ?: freshLabel(prefix = "S").also { strings[value] = it }, value)

  private fun freshIdx(): Int = labelIdx.also { labelIdx += 1 }
  private fun freshLabel(prefix: String): Label = "$prefix${freshIdx()}".label

  private fun addArg(label: Label, type: Type, currOffset: Int): ArgValue =
    ArgValue(label.name, currOffset, type).also { varEnv[label.name] = it }

  private fun addLocal(label: Label, type: Type): LocalValue =
    LocalValue("${label.name}@${freshIdx()}", type).also { varEnv[label.name] = it }

  private fun AstNode.getVar(name: String): VirtualReg = varEnv[name] ?: err("Not defined variable with name $name")
  private fun AstNode.getFunType(name: String): Type = funEnv[name] ?: err("Not defined function with name $name")
}

private inline val Type.default: ConstValue
  get() = when (this) {
    BooleanType -> false.bool
    IntType -> IntConstValue(0)
    StringType -> StringConstValue(EMPTY_STRING_LABEL, "")
    VoidType -> err("No default value for void type as it cannot be assigned")
    is RefType -> TODO()
  }

val EMPTY_STRING_LABEL: Label = "S@EMPTY".label

private fun AstNode.err(message: String): Nothing = throw IRException(LocalizedMessage(message, span?.from))
