package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.syntax.LatteBaseVisitor
import ml.dev.kotlin.syntax.LatteParser
import org.antlr.v4.runtime.tree.TerminalNode

object AstVisitor : LatteBaseVisitor<AstNode>() {
  override fun visitProgram(ctx: LatteParser.ProgramContext) = Program(ctx.topDef().map { visitTopDef(it) })

  override fun visitTopDef(ctx: LatteParser.TopDefContext) =
    TopDef(ctx.type_().visit(), ctx.ID().visit(), visitArg(ctx.arg()), visitBlock(ctx.block()))

  override fun visitArg(ctx: LatteParser.ArgContext?) =
    Args(ctx?.type_().orEmpty().zip(ctx?.ID().orEmpty()).map { (type, ident) -> Arg(type.visit(), ident.visit()) })

  override fun visitBlock(ctx: LatteParser.BlockContext) = Block(ctx.stmt().map { it.visit() })
  override fun visitEmpty(ctx: LatteParser.EmptyContext) = EmptyStmt
  override fun visitBlockStmt(ctx: LatteParser.BlockStmtContext) = BlockStmt(visitBlock(ctx.block()))
  override fun visitAss(ctx: LatteParser.AssContext) = AssStmt(ctx.ID().visit(), ctx.expr().visit())
  override fun visitIncr(ctx: LatteParser.IncrContext) = IncrStmt(ctx.ID().visit())
  override fun visitDecr(ctx: LatteParser.DecrContext) = DecrStmt(ctx.ID().visit())
  override fun visitRet(ctx: LatteParser.RetContext) = RetStmt(ctx.expr().visit())
  override fun visitVRet(ctx: LatteParser.VRetContext) = VRetStmt
  override fun visitSExp(ctx: LatteParser.SExpContext) = ExprStmt(ctx.expr().visit())
  override fun visitDecl(ctx: LatteParser.DeclContext) = DeclStmt(ctx.type_().visit(), ctx.item().map { visitItem(it) })
  override fun visitCond(ctx: LatteParser.CondContext) = when (val expr = ctx.expr().visit()) {
    BoolExpr(false) -> EmptyStmt
    else -> CondStmt(expr, ctx.stmt().visit())
  }

  override fun visitWhile(ctx: LatteParser.WhileContext) = when (val expr = ctx.expr().visit()) {
    BoolExpr(false) -> EmptyStmt
    else -> WhileStmt(expr, ctx.stmt().visit())
  }

  override fun visitCondElse(ctx: LatteParser.CondElseContext) = when (val expr = ctx.expr().visit()) {
    BoolExpr(true) -> ctx.stmt(0).visit()
    BoolExpr(false) -> ctx.stmt(1).visit()
    else -> CondElseStmt(expr, ctx.stmt(0).visit(), ctx.stmt(1).visit())
  }

  override fun visitItem(ctx: LatteParser.ItemContext) =
    ctx.expr()?.visit()?.let { InitItem(ctx.ID().visit(), it) } ?: NotInitItem(ctx.ID().visit())

  override fun visitEFunCall(ctx: LatteParser.EFunCallContext) =
    FunCallExpr(ctx.ID().visit(), ctx.expr().orEmpty().map { it.visit() })

  override fun visitERelOp(ctx: LatteParser.ERelOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.relOp().visit(), ctx.expr(1).visit())

  override fun visitEAddOp(ctx: LatteParser.EAddOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.addOp().visit(), ctx.expr(1).visit())

  override fun visitEMulOp(ctx: LatteParser.EMulOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.mulOp().visit(), ctx.expr(1).visit())

  override fun visitEAnd(ctx: LatteParser.EAndContext) =
    BinOpExpr(ctx.expr(0).visit(), BooleanOp.And, ctx.expr(1).visit())

  override fun visitEOr(ctx: LatteParser.EOrContext) =
    BinOpExpr(ctx.expr(0).visit(), BooleanOp.Or, ctx.expr(1).visit())

  override fun visitEId(ctx: LatteParser.EIdContext) = IdentExpr(ctx.ID().visit())
  override fun visitEStr(ctx: LatteParser.EStrContext) = StringExpr(ctx.STR().text.run { substring(1, length - 1) })
  override fun visitEUnOp(ctx: LatteParser.EUnOpContext) = UnOpExpr(ctx.unOp().visit(), ctx.expr().visit())
  override fun visitEParen(ctx: LatteParser.EParenContext) = ctx.expr().visit()
  override fun visitEInt(ctx: LatteParser.EIntContext) = IntExpr(ctx.INT().visit())
  override fun visitETrue(ctx: LatteParser.ETrueContext) = BoolExpr(true)
  override fun visitEFalse(ctx: LatteParser.EFalseContext) = BoolExpr(false)
  override fun visitInt(ctx: LatteParser.IntContext) = IntType
  override fun visitStr(ctx: LatteParser.StrContext) = StringType
  override fun visitBool(ctx: LatteParser.BoolContext) = BooleanType
  override fun visitVoid(ctx: LatteParser.VoidContext) = VoidType
  override fun visitNot(ctx: LatteParser.NotContext) = UnOp.Not
  override fun visitNeg(ctx: LatteParser.NegContext) = UnOp.Neg
  override fun visitPlus(ctx: LatteParser.PlusContext) = NumOp.Plus
  override fun visitMinus(ctx: LatteParser.MinusContext) = NumOp.Minus
  override fun visitTimes(ctx: LatteParser.TimesContext) = NumOp.Times
  override fun visitDivide(ctx: LatteParser.DivideContext) = NumOp.Divide
  override fun visitMod(ctx: LatteParser.ModContext) = NumOp.Mod
  override fun visitLT(ctx: LatteParser.LTContext) = RelOp.LT
  override fun visitLE(ctx: LatteParser.LEContext) = RelOp.LE
  override fun visitGT(ctx: LatteParser.GTContext) = RelOp.GT
  override fun visitGE(ctx: LatteParser.GEContext) = RelOp.GE
  override fun visitEQ(ctx: LatteParser.EQContext) = RelOp.EQ
  override fun visitNE(ctx: LatteParser.NEContext) = RelOp.NE

  private fun LatteParser.StmtContext.visit() = accept(this@AstVisitor) as Stmt
  private fun LatteParser.ExprContext.visit() = accept(this@AstVisitor) as Expr
  private fun LatteParser.Type_Context.visit() = accept(this@AstVisitor) as Type
  private fun LatteParser.AddOpContext.visit() = accept(this@AstVisitor) as NumOp
  private fun LatteParser.MulOpContext.visit() = accept(this@AstVisitor) as NumOp
  private fun LatteParser.RelOpContext.visit() = accept(this@AstVisitor) as RelOp
  private fun LatteParser.UnOpContext.visit() = accept(this@AstVisitor) as UnOp
  private fun TerminalNode.visit() = SpannedText(text, symbol.line, symbol.charPositionInLine)
}
