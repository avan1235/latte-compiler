package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.ExceptionLocalizedMessage
import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.unit

fun TypeCheckedProgram.toIR() = IRGenerator().run { this@toIR.generate() }

private data class IRGenerator(
  private val funEnv: MutableMap<String, Type> = stdLibFunctionTypes(),
  private val quadruples: MutableList<Quadruple> = mutableListOf(),
  private val varEnv: StackTable<String, MemoryLoc> = StackTable(),
  private val strings: MutableMap<String, Label> = hashMapOf(),
  private var labelIdx: Int = 0,
  private var localIdx: Int = 0,
) {
  fun TypeCheckedProgram.generate(): QuadruplesList {
    program.topDefs.onEach { it.addToFunEnv() }.forEach { it.generate() }
    return QuadruplesList(quadruples, strings)
  }

  private fun TopDef.addToFunEnv() {
    funEnv[mangledName] = type
  }

  private fun TopDef.generate() = varEnv.level {
    val args = args.list.mapIndexed { idx, (type, name) -> addArg(name.label, type, idx) }
    emit { CodeFunLabelQ(mangledName.label, args) }
    block.generate()
  }

  private fun Block.generate(): Unit = stmts.forEach { it.generate() }

  private fun Stmt.generate(): Unit = when (this) {
    EmptyStmt -> Unit
    is BlockStmt -> varEnv.level { block.generate() }
    is DeclStmt -> items.forEach { it.generate(type) }
    is AssStmt -> emit { AssignQ(getVar(ident), expr.generate()) }
    is DecrStmt -> emit { DecQ(getVar(ident)) }
    is IncrStmt -> emit { IncQ(getVar(ident)) }
    is ExprStmt -> expr.generate().unit()
    is RetStmt -> emit { RetQ(expr.generate().inMemory()) }
    is VRetStmt -> emit { RetQ() }
    is CondStmt -> {
      val (trueLabel, endLabel) = freshLabels(count = 2)
      generateCond(expr, trueLabel, endLabel)

      emit { CodeLabelQ(trueLabel) }
      onTrue.generate()

      emit { CodeLabelQ(endLabel) }
    }
    is CondElseStmt -> {
      val (trueLabel, falseLabel, endLabel) = freshLabels(count = 3)
      generateCond(expr, trueLabel, falseLabel)

      emit { CodeLabelQ(falseLabel) }
      onFalse.generate()
      emit { JumpQ(endLabel) }

      emit { CodeLabelQ(trueLabel) }
      onTrue.generate()

      emit { CodeLabelQ(endLabel) }
    }
    is WhileStmt -> {
      val (body, condition, endWhile) = freshLabels(count = 3)
      emit { JumpQ(condition) }

      emit { CodeLabelQ(body) }
      onTrue.generate()

      emit { CodeLabelQ(condition) }
      generateCond(expr, body, endWhile)

      emit { CodeLabelQ(endWhile) }
    }
  }

  private fun generateCond(expr: Expr, onTrue: Label, onFalse: Label): Unit = when {
    expr is UnOpExpr && expr.op == UnOp.NOT -> generateCond(expr.expr, onFalse, onTrue)
    expr is BinOpExpr && expr.op is RelOp -> {
      val lv = expr.left.generate().inMemory()
      val rv = expr.right.generate().inMemory()
      emit { BiCondJumpQ(lv, expr.op, rv, onTrue) }
      emit { JumpQ(onFalse) }
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
    is StringExpr -> addStringConst(value.str)
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
          lv is IntConstValue && rv is IntConstValue -> op.on(lv, rv)
          lv is StringConstValue && rv is StringConstValue -> addStringConst(lv + rv)
          lv is BooleanConstValue && rv is BooleanConstValue -> op.on(lv, rv)
          else -> when {
            op == NumOp.PLUS && lv.type == IntType && rv.type == IntType -> IntType
            op == NumOp.PLUS && lv.type == StringType && rv.type == StringType -> StringType
            op is BooleanOp -> BooleanType
            op is NumOp -> IntType
            op is RelOp -> BooleanType
            else -> err("Unknown binary operation $this")
          }.let { type -> freshTemp(type) { to -> emit { BinOpQ(to, lv.inMemory(), op, rv.inMemory()) } } }
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
      AssStmt(to.label.name, BoolExpr(true)),
      AssStmt(to.label.name, BoolExpr(false)),
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
    TempValue(freshLabel(prefix = "T"), type).also(action)

  private fun addStringConst(value: StringConstValue): StringConstValue =
    value.also { strings[it.str] = freshLabel(prefix = "S") }

  private fun freshLabel(prefix: String): Label = "$prefix$labelIdx".also { labelIdx += 1 }.label

  private fun freshLabels(count: Int, prefix: String = "L"): List<Label> = List(count) { freshLabel(prefix) }

  private fun addArg(label: Label, type: Type, idx: Int): ArgValue =
    ArgValue(idx, type).also { varEnv[label.name] = it }

  private fun addLocal(label: Label, type: Type): LocalValue =
    LocalValue(localIdx, type).also { varEnv[label.name] = it }.also { localIdx += 1 }

  private fun AstNode.getVar(name: String): MemoryLoc = varEnv[name] ?: err("Not defined variable with name $name")
  private fun AstNode.getFunType(name: String): Type = funEnv[name] ?: err("Not defined function with name $name")
}

private fun BinOp.on(lv: IntConstValue, rv: IntConstValue) = when (this) {
  NumOp.PLUS -> lv + rv
  NumOp.MINUS -> lv - rv
  NumOp.TIMES -> lv * rv
  NumOp.DIVIDE -> lv / rv
  NumOp.MOD -> lv % rv
  RelOp.LT -> (lv < rv).bool
  RelOp.LE -> (lv <= rv).bool
  RelOp.GT -> (lv > rv).bool
  RelOp.GE -> (lv >= rv).bool
  RelOp.EQ -> (lv == rv).bool
  RelOp.NE -> (lv != rv).bool
  else -> err("Undefined $this for $lv and $rv")
}

private fun BinOp.on(lv: BooleanConstValue, rv: BooleanConstValue) = when (this) {
  RelOp.LT -> (lv < rv).bool
  RelOp.LE -> (lv <= rv).bool
  RelOp.GT -> (lv > rv).bool
  RelOp.GE -> (lv >= rv).bool
  RelOp.EQ -> (lv == rv).bool
  RelOp.NE -> (lv != rv).bool
  else -> err("Undefined $this for $lv and $rv")
}

private fun AstNode.err(message: String): Nothing = throw IRException(ExceptionLocalizedMessage(message, span?.from))
