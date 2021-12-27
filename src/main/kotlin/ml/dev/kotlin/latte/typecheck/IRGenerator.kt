package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.StackTable
import ml.dev.kotlin.latte.util.unit

fun Program.toIR() = IRGenerator().include(this)

private data class IRGenerator(
  private val quadruples: MutableList<Quadruple> = mutableListOf(),
  private val varEnv: StackTable<String, String> = StackTable(),
  private var idx: Int = 0,
) {
  private val stringLiterals = MutableDefaultMap<String, Label>({ nextName() })

  fun include(program: Program) {
//    program.topDefs.forEach { include(it) }
  }

  private fun include(block: Block): Unit = block.stmts.forEach { include(it) }

  private fun include(stmt: Stmt): Unit = when (stmt) {
    EmptyStmt -> Unit
    is DeclStmt -> stmt.items.forEach { include(it) }
    is AssStmt -> emit { AssignQ(stmt.ident.label, include(stmt.expr)) }
    is BlockStmt -> varEnv.level { include(stmt.block) }
    is DecrStmt -> emit { DecQ(stmt.ident.label) }
    is IncrStmt -> emit { IncQ(stmt.ident.label) }
    is ExprStmt -> include(stmt.expr).unit()
    is RetStmt -> emit { RetQ(include(stmt.expr)) }
    is VRetStmt -> emit { RetQ() }
    is CondElseStmt -> TODO()
    is CondStmt -> TODO()
    is WhileStmt -> TODO()
  }

  private fun include(item: Item) = when (item) {
    is NotInitItem -> Unit
    is InitItem -> quadruples += AssignQ(item.ident.label, include(item.expr))
  }

  private fun include(expr: Expr): Label = when (expr) {
    is BinOpExpr -> when (expr.opExpr) {
      BooleanOp.And -> TODO()
      BooleanOp.Or -> TODO()
      else -> withNewName { to -> BinOpQ(to, include(expr.left), expr.opExpr, include(expr.right)) }
    }
    is UnOpExpr -> withNewName { to -> UnOpQ(to, expr.op, include(expr.expr)) }
    is BoolExpr -> withNewName { to -> AssignQ(to, BooleanValue(expr.value)) }
    is IntExpr -> withNewName { to -> AssignQ(to, IntValue(expr.value)) }
    is StringExpr -> withNewName { to -> AssignQ(to, stringLiterals[expr.value]) }
    is IdentExpr -> withNewName { to -> AssignQ(to, expr.text.label) }
    is FunCallExpr -> withNewName { to -> FunCallQ(to, expr.name.label, expr.args.map { include(it) }) }
  }

  private inline fun withNewName(emit: (Label) -> Quadruple): Label = nextName().also { quadruples += emit(it) }
  private inline fun emit(emit: () -> Quadruple) {
    quadruples += emit()
  }

  private fun nextName(): Label = "l$idx".label.also { idx += 1 }
}
