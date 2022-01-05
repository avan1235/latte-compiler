package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.FileLocation
import ml.dev.kotlin.latte.util.Span
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

object AstVisitor : LatteBaseVisitor<AstNode>() {
  override fun visitProgram(ctx: LatteParser.ProgramContext): Program =
    Program(ctx.topDef().map { it.visit() }, ctx.span())

  override fun visitFun(ctx: LatteParser.FunContext): FunDef =
    FunDef(ctx.type().visit(), ctx.ID().visit(), visitArg(ctx.arg()), visitBlock(ctx.block()), ctx.span())

  override fun visitClass(ctx: LatteParser.ClassContext): ClassDef = ctx.classDef().visitReturning()

  override fun visitClassNotExtendingDef(ctx: LatteParser.ClassNotExtendingDefContext): ClassDef {
    val bodyDefs = ctx.classBodyDef().map { it.visitReturning<AstNode>() }
    val fields = bodyDefs.filterIsInstance<ClassField>()
    val methods = bodyDefs.filterIsInstance<MethodDef>()
    return ClassDef(ctx.ID().visit(), fields, methods, parentClass = null, ctx.span())
  }

  override fun visitClassExtendingDef(ctx: LatteParser.ClassExtendingDefContext): AstNode {
    val (name, parent) = ctx.ID().map { it.visit() }
    val bodyDefs = ctx.classBodyDef().map { it.visitReturning<AstNode>() }
    val fields = bodyDefs.filterIsInstance<ClassField>()
    val methods = bodyDefs.filterIsInstance<MethodDef>()
    return ClassDef(name, fields, methods, parent, ctx.span())
  }

  override fun visitEClassConstructorCall(ctx: LatteParser.EClassConstructorCallContext): ConstructorCallExpr =
    ConstructorCallExpr(ctx.type().visit(), ctx.span())

  override fun visitEClassMethodCall(ctx: LatteParser.EClassMethodCallContext): MethodCallExpr {
    val expressions = ctx.expr().map { it.visit() }
    return MethodCallExpr(expressions.first(), ctx.ID().visit(), expressions.drop(1), ctx.span())
  }

  override fun visitRef(ctx: LatteParser.RefContext): RefType = RefType(ctx.ID().visit(), ctx.span())

  override fun visitClassField(ctx: LatteParser.ClassFieldContext): ClassField =
    ClassField(ctx.type().visit(), ctx.ID().visit(), ctx.span())

  override fun visitClassMethod(ctx: LatteParser.ClassMethodContext): MethodDef =
    MethodDef(ctx.type().visit(), ctx.ID().visit(), visitArg(ctx.arg()), visitBlock(ctx.block()), ctx.span())

  override fun visitArg(ctx: LatteParser.ArgContext?) = Args(
    ctx?.type().orEmpty().zip(ctx?.ID().orEmpty()).map { (type, ident) -> Arg(type.visit(), ident.visit()) },
    ctx?.span()
  )

  override fun visitBlock(ctx: LatteParser.BlockContext) =
    Block(ctx.stmt().map { it.visit() }.filterTo(ArrayList()) { it != EmptyStmt }, ctx.span())

  override fun visitEmpty(ctx: LatteParser.EmptyContext) = EmptyStmt
  override fun visitBlockStmt(ctx: LatteParser.BlockStmtContext) = BlockStmt(visitBlock(ctx.block()), ctx.span())
  override fun visitAss(ctx: LatteParser.AssContext) = AssStmt(ctx.ID().visit(), ctx.expr().visit(), ctx.span())
  override fun visitIncr(ctx: LatteParser.IncrContext) = IncrStmt(ctx.ID().visit(), ctx.span())
  override fun visitDecr(ctx: LatteParser.DecrContext) = DecrStmt(ctx.ID().visit(), ctx.span())
  override fun visitRet(ctx: LatteParser.RetContext) = RetStmt(ctx.expr().visit(), ctx.span())
  override fun visitVRet(ctx: LatteParser.VRetContext) = VRetStmt(ctx.span())
  override fun visitSExp(ctx: LatteParser.SExpContext) = ExprStmt(ctx.expr().visit(), ctx.span())
  override fun visitRefAss(ctx: LatteParser.RefAssContext) =
    ctx.expr().map { it.visit() }.let { expr -> RefAssStmt(expr.first(), ctx.ID().visit(), expr.last(), ctx.span()) }

  override fun visitDecl(ctx: LatteParser.DeclContext) =
    DeclStmt(ctx.type().visit(), ctx.item().map { visitItem(it) }, ctx.span())

  override fun visitCond(ctx: LatteParser.CondContext) = when (val expr = ctx.expr().visit()) {
    is BoolExpr -> if (!expr.value) EmptyStmt else ctx.stmt().visit()
    else -> CondStmt(expr, ctx.stmt().visit(), ctx.span())
  }

  override fun visitWhile(ctx: LatteParser.WhileContext) = when (val expr = ctx.expr().visit()) {
    is BoolExpr -> if (!expr.value) EmptyStmt else WhileStmt(expr, ctx.stmt().visit(), ctx.span())
    else -> WhileStmt(expr, ctx.stmt().visit(), ctx.span())
  }

  override fun visitCondElse(ctx: LatteParser.CondElseContext) = when (val expr = ctx.expr().visit()) {
    is BoolExpr -> if (expr.value) ctx.stmt(0).visit() else ctx.stmt(1).visit()
    else -> CondElseStmt(expr, ctx.stmt(0).visit(), ctx.stmt(1).visit(), ctx.span())
  }

  override fun visitItem(ctx: LatteParser.ItemContext) =
    ctx.expr()?.visit()?.let { InitItem(ctx.ID().visit(), it, ctx.span()) } ?: NotInitItem(ctx.ID().visit(), ctx.span())

  override fun visitEFunCall(ctx: LatteParser.EFunCallContext) =
    FunCallExpr(ctx.ID().visit(), ctx.expr().orEmpty().map { it.visit() }, ctx.span())

  override fun visitERelOp(ctx: LatteParser.ERelOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.relOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEAddOp(ctx: LatteParser.EAddOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.addOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEMulOp(ctx: LatteParser.EMulOpContext) =
    BinOpExpr(ctx.expr(0).visit(), ctx.mulOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEAnd(ctx: LatteParser.EAndContext) =
    BinOpExpr(ctx.expr(0).visit(), BooleanOp.AND, ctx.expr(1).visit(), ctx.span())

  override fun visitEOr(ctx: LatteParser.EOrContext) =
    BinOpExpr(ctx.expr(0).visit(), BooleanOp.OR, ctx.expr(1).visit(), ctx.span())

  override fun visitEClassField(ctx: LatteParser.EClassFieldContext): FieldExpr =
    FieldExpr(ctx.expr().visit(), ctx.ID().visit(), ctx.span())

  override fun visitECast(ctx: LatteParser.ECastContext): CastExpr =
    CastExpr(ctx.type().visit(), ctx.expr().visit(), ctx.span())

  override fun visitEStr(ctx: LatteParser.EStrContext) =
    StringExpr(ctx.STR().text.run { substring(1, length - 1) }, ctx.span())

  override fun visitENull(ctx: LatteParser.ENullContext): NullExpr = NullExpr(ctx.span())
  override fun visitEId(ctx: LatteParser.EIdContext) = IdentExpr(ctx.ID().visit(), ctx.span())
  override fun visitEUnOp(ctx: LatteParser.EUnOpContext) = UnOpExpr(ctx.unOp().visit(), ctx.expr().visit(), ctx.span())
  override fun visitEParen(ctx: LatteParser.EParenContext) = ctx.expr().visit()
  override fun visitEInt(ctx: LatteParser.EIntContext) = IntExpr(ctx.INT().visit(), ctx.span())
  override fun visitETrue(ctx: LatteParser.ETrueContext) = BoolExpr(true, ctx.span())
  override fun visitEFalse(ctx: LatteParser.EFalseContext) = BoolExpr(false, ctx.span())
  override fun visitInt(ctx: LatteParser.IntContext) = IntType
  override fun visitStr(ctx: LatteParser.StrContext) = StringType
  override fun visitBool(ctx: LatteParser.BoolContext) = BooleanType
  override fun visitVoid(ctx: LatteParser.VoidContext) = VoidType
  override fun visitNot(ctx: LatteParser.NotContext) = UnOp.NOT
  override fun visitNeg(ctx: LatteParser.NegContext) = UnOp.NEG
  override fun visitPlus(ctx: LatteParser.PlusContext) = NumOp.PLUS
  override fun visitMinus(ctx: LatteParser.MinusContext) = NumOp.MINUS
  override fun visitTimes(ctx: LatteParser.TimesContext) = NumOp.TIMES
  override fun visitDivide(ctx: LatteParser.DivideContext) = NumOp.DIVIDE
  override fun visitMod(ctx: LatteParser.ModContext) = NumOp.MOD
  override fun visitLT(ctx: LatteParser.LTContext) = RelOp.LT
  override fun visitLE(ctx: LatteParser.LEContext) = RelOp.LE
  override fun visitGT(ctx: LatteParser.GTContext) = RelOp.GT
  override fun visitGE(ctx: LatteParser.GEContext) = RelOp.GE
  override fun visitEQ(ctx: LatteParser.EQContext) = RelOp.EQ
  override fun visitNE(ctx: LatteParser.NEContext) = RelOp.NE

  private fun LatteParser.StmtContext.visit() = visitReturning<Stmt>()
  private fun LatteParser.ExprContext.visit() = visitReturning<Expr>()
  private fun LatteParser.TypeContext.visit() = visitReturning<Type>()
  private fun LatteParser.AddOpContext.visit() = visitReturning<NumOp>()
  private fun LatteParser.MulOpContext.visit() = visitReturning<NumOp>()
  private fun LatteParser.RelOpContext.visit() = visitReturning<RelOp>()
  private fun LatteParser.UnOpContext.visit() = visitReturning<UnOp>()
  private fun LatteParser.TopDefContext.visit() = visitReturning<TopDef>()

  private inline fun <reified T> ParserRuleContext.visitReturning() = accept(this@AstVisitor) as T
  private fun Token.loc() = FileLocation(line, charPositionInLine)
  private fun ParserRuleContext.span() = Span(start.loc(), stop.loc())
  private fun TerminalNode.visit() = text
}
