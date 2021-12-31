package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.quadruple.ControlFlowGraph.Companion.buildCFG
import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.typecheck.TypeCheckedProgram
import ml.dev.kotlin.latte.typecheck.stdLibFunctionTypes
import ml.dev.kotlin.latte.util.*

fun TypeCheckedProgram.toIR(): IR = IRGenerator().run { this@toIR.generate() }

data class IR(val graph: ControlFlowGraph, val strings: Map<String, Label>)

private data class IRGenerator(
  private val funEnv: MutableMap<String, Type> = stdLibFunctionTypes(),
  private val quadruples: MutableList<Quadruple> = mutableListOf(),
  private val varEnv: StackTable<String, MemoryLoc> = StackTable(),
  private val strings: MutableMap<String, Label> = hashMapOf(),
  private var labelIdx: Int = 0,
) {
  fun TypeCheckedProgram.generate(): IR {
    program.topDefs.onEach { it.addToFunEnv() }.forEach { it.generate() }
    val cfg = quadruples.buildCFG { CodeLabelQ(freshLabel(prefix = "G")) }
    return IR(cfg, strings)
  }

  private fun TopDef.addToFunEnv() {
    funEnv[mangledName] = type
  }

  private fun TopDef.generate() = varEnv.onLevel {
    val args = args.list.mapIndexed { idx, (type, name) -> addArg(name.label, type, idx) }
    emit { FunCodeLabelQ(mangledName.label, args) }
    block.generate()
  }

  private fun Block.generate(): Unit = stmts.forEach { it.generate() }

  private fun Stmt.generate(): Unit = when (this) {
    EmptyStmt -> Unit
    is BlockStmt -> varEnv.onLevel { block.generate() }
    is DeclStmt -> items.forEach { it.generate(type) }
    is AssStmt -> emit { AssignQ(getVar(ident), expr.generate()) }
    is DecrStmt -> emit { getVar(ident).let { UnOpModQ(it, UnOpMod.DEC, it) } }
    is IncrStmt -> emit { getVar(ident).let { UnOpModQ(it, UnOpMod.INC, it) } }
    is ExprStmt -> expr.generate().unit()
    is RetStmt -> emit { RetQ(expr.generate()) }
    is VRetStmt -> emit { RetQ() }
    is CondStmt -> {
      val (trueLabel, endLabel) = get(count = 2) { freshLabel(prefix = "L") }
      generateCond(expr, trueLabel, endLabel)

      emit { CodeLabelQ(trueLabel) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(endLabel) }
    }
    is CondElseStmt -> {
      val (trueLabel, falseLabel, endLabel) = get(count = 3) { freshLabel(prefix = "L") }
      generateCond(expr, trueLabel, falseLabel)

      emit { CodeLabelQ(falseLabel) }
      varEnv.onLevel { onFalse.generate() }
      emit { JumpQ(endLabel) }

      emit { CodeLabelQ(trueLabel) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(endLabel) }
    }
    is WhileStmt -> {
      val (body, condition, endWhile) = get(count = 3) { freshLabel(prefix = "L") }
      emit { JumpQ(condition) }

      emit { CodeLabelQ(body) }
      varEnv.onLevel { onTrue.generate() }

      emit { CodeLabelQ(condition) }
      generateCond(expr, body, endWhile)

      emit { CodeLabelQ(endWhile) }
    }
  }

  private fun generateCond(expr: Expr, onTrue: Label, onFalse: Label): Unit = when {
    expr is UnOpExpr && expr.op == UnOp.NOT -> generateCond(expr.expr, onFalse, onTrue)
    expr is BinOpExpr && expr.op is RelOp -> {
      val lv = expr.left.generate()
      val rv = expr.right.generate()
      when {
        lv is MemoryLoc && rv is MemoryLoc && lv == rv && expr.op == RelOp.EQ -> emit { JumpQ(onTrue) }
        lv is MemoryLoc && rv is MemoryLoc && lv == rv && expr.op == RelOp.NE -> emit { JumpQ(onFalse) }
        lv is BooleanConstValue && rv is BooleanConstValue ->
          if (expr.op.rel(lv, rv).bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
        lv is IntConstValue && rv is IntConstValue ->
          if (expr.op.rel(lv, rv).bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
        else -> {
          emit { RelCondJumpQ(lv.inMemory(), expr.op, rv, onTrue) }
          emit { JumpQ(onFalse) }
        }
      }
    }
    expr is BinOpExpr && expr.op == BooleanOp.AND -> {
      val midLabel = freshLabel(prefix = "M")
      generateCond(expr.left, midLabel, onFalse)
      emit { CodeLabelQ(midLabel) }
      generateCond(expr.right, onTrue, onFalse)
    }
    expr is BinOpExpr && expr.op == BooleanOp.OR -> {
      val midLabel = freshLabel(prefix = "M")
      generateCond(expr.left, onTrue, midLabel)
      emit { CodeLabelQ(midLabel) }
      generateCond(expr.right, onTrue, onFalse)
    }
    else -> when (val cond = expr.generate()) {
      is BooleanConstValue -> if (cond.bool) emit { JumpQ(onTrue) } else emit { JumpQ(onFalse) }
      else -> {
        emit { CondJumpQ(cond.inMemory(), onTrue) }
        emit { JumpQ(onFalse) }
      }
    }
  }

  private fun Item.generate(type: Type): Unit = when (this) {
    is NotInitItem -> addLocal(ident.label, type).unit()
    is InitItem -> emit {
      val value = expr.generate()
      AssignQ(addLocal(ident.label, type), value)
    }
  }

  private fun Expr.generate(): ValueHolder = when (this) {
    is FunCallExpr -> freshTemp(getFunType(mangledName)) { to ->
      emit { FunCallQ(to, mangledName.label, args.map { it.generate() }) }
    }
    is IdentExpr -> getVar(value)
    is BoolExpr -> value.bool
    is IntExpr -> value.int
    is StringExpr -> addStringConst(value)
    is UnOpExpr -> {
      val from = expr.generate()
      when (op) {
        UnOp.NEG ->
          if (from is IntConstValue) -from
          else freshTemp(IntType) { to -> emit { UnOpQ(to, op, from.inMemory()) } }
        UnOp.NOT ->
          if (from is BooleanConstValue) !from
          else freshTemp(BooleanType) { to -> emit { UnOpQ(to, op, from.inMemory()) } }
      }
    }
    is BinOpExpr -> when (op) {
      BooleanOp.AND -> left lazyAnd right
      BooleanOp.OR -> left lazyOr right
      else -> {
        val lv = left.generate()
        val rv = right.generate()
        when {
          lv is IntConstValue && rv is IntConstValue && op is RelOp -> op.rel(lv, rv)
          lv is IntConstValue && rv is IntConstValue && op is NumOp -> op.num(lv, rv)
          lv is StringConstValue && rv is StringConstValue -> addStringConst(lv.str + rv.str)
          lv is BooleanConstValue && rv is BooleanConstValue && op is RelOp -> op.rel(lv, rv)
          else -> when {
            op is NumOp && op == NumOp.PLUS && lv.type == StringType && rv.type == StringType ->
              freshTemp(StringType) { to -> emit { BinOpQ(to, lv.inMemory(), op, rv) } }
            op is NumOp -> freshTemp(IntType) { to -> emit { BinOpQ(to, lv.inMemory(), op, rv) } }
            op is RelOp -> freshTemp(BooleanType) { to ->
              val falseLabel = freshLabel(prefix = "F")
              emit { AssignQ(to, false.bool) }
              emit { RelCondJumpQ(lv.inMemory(), op.rev, rv, falseLabel) }
              emit { AssignQ(to, true.bool) }
              emit { CodeLabelQ(falseLabel) }
            }
            else -> err("Unknown binary operation $this")
          }
        }
      }
    }
  }

  private infix fun Expr.lazyAnd(right: Expr): ValueHolder {
    val lv = generate()
    return when {
      lv is BooleanConstValue && !lv.bool -> lv
      lv is BooleanConstValue && lv.bool -> right.generate()
      else -> generateCondElse(this, BooleanOp.AND, right)
    }
  }

  private infix fun Expr.lazyOr(right: Expr): ValueHolder {
    val lv = generate()
    return when {
      lv is BooleanConstValue && lv.bool -> lv
      lv is BooleanConstValue && !lv.bool -> right.generate()
      else -> generateCondElse(this, BooleanOp.OR, right)
    }
  }

  private fun generateCondElse(left: Expr, op: BooleanOp, right: Expr): TempValue = freshTemp(BooleanType) { to ->
    CondElseStmt(
      BinOpExpr(left, op, right),
      AssStmt(to.name, BoolExpr(true)),
      AssStmt(to.name, BoolExpr(false)),
    ).generate()
  }

  private inline fun emit(emit: () -> Quadruple) {
    quadruples += emit()
  }

  private fun ValueHolder.inMemory(): MemoryLoc = when (this) {
    is IntConstValue -> freshTemp(IntType) { to -> emit { AssignQ(to, this) } }
    is StringConstValue -> freshTemp(StringType) { to -> emit { AssignQ(to, this) } }
    is BooleanConstValue -> freshTemp(BooleanType) { to -> emit { AssignQ(to, this) } }
    is MemoryLoc -> this
  }

  private fun freshTemp(type: Type, action: (TempValue) -> Unit = {}): TempValue =
    freshIdx().let { TempValue("@T$it", it, type) }.also { varEnv[it.name] = it }.also(action)

  private fun addStringConst(value: String): StringConstValue =
    StringConstValue(strings[value] ?: freshLabel(prefix = "S").also { strings[value] = it }, value)

  private fun freshIdx(): Int = labelIdx.also { labelIdx += 1 }
  private fun freshLabel(prefix: String): Label = "@$prefix${freshIdx()}".label

  private fun addArg(label: Label, type: Type, idx: Int): ArgValue =
    ArgValue(label.name, idx, type).also { varEnv[label.name] = it }

  private fun addLocal(label: Label, type: Type): LocalValue =
    LocalValue("${label.name}@${varEnv.level}", varEnv.level, type).also { varEnv[label.name] = it }

  private fun AstNode.getVar(name: String): MemoryLoc = varEnv[name] ?: err("Not defined variable with name $name")
  private fun AstNode.getFunType(name: String): Type = funEnv[name] ?: err("Not defined function with name $name")
}

private fun AstNode.err(message: String): Nothing = throw IRException(LocalizedMessage(message, span?.from))
