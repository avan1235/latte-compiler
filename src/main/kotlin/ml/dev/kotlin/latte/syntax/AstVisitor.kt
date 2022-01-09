package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.util.FileLocation
import ml.dev.kotlin.latte.util.Span
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode

object AstVisitor : LatteBaseVisitor<AstNode>() {
  override fun visitProgram(ctx: LatteParser.ProgramContext): ProgramNode =
    ProgramNode(ctx.topDef().map { it.visit() }, ctx.span())

  override fun visitFun(ctx: LatteParser.FunContext): FunDefNode =
    FunDefNode(ctx.type().visit(), ctx.ID().visit(), visitArg(ctx.arg()), visitBlock(ctx.block()), ctx.span())

  override fun visitClass(ctx: LatteParser.ClassContext): ClassDefNode = ctx.classDef().visitReturning()

  override fun visitClassNotExtendingDef(ctx: LatteParser.ClassNotExtendingDefContext): ClassDefNode {
    val bodyDefs = ctx.classBodyDef().map { it.visitReturning<AstNode>() }
    val fields = bodyDefs.filterIsInstance<ClassFieldNode>()
    val methods = bodyDefs.filterIsInstance<FunDefNode>()
    return ClassDefNode(ctx.ID().visit(), fields, methods, parentClass = null, ctx.span())
  }

  override fun visitClassExtendingDef(ctx: LatteParser.ClassExtendingDefContext): AstNode {
    val (name, parent) = ctx.ID().map { it.visit() }
    val bodyDefs = ctx.classBodyDef().map { it.visitReturning<AstNode>() }
    val fields = bodyDefs.filterIsInstance<ClassFieldNode>()
    val methods = bodyDefs.filterIsInstance<FunDefNode>()
    return ClassDefNode(name, fields, methods, parent, ctx.span())
  }

  override fun visitEClassConstructorCall(ctx: LatteParser.EClassConstructorCallContext): ConstructorCallExprNode =
    ConstructorCallExprNode(ctx.type().visit(), ctx.span())

  override fun visitEClassMethodCall(ctx: LatteParser.EClassMethodCallContext): MethodCallExprNode {
    val expressions = ctx.expr().map { it.visit() }
    return MethodCallExprNode(expressions.first(), ctx.ID().visit(), expressions.drop(1), ctx.span())
  }

  override fun visitClassType(ctx: LatteParser.ClassTypeContext): ClassType = ClassType(ctx.ID().visit(), ctx.span())
  override fun visitClassField(ctx: LatteParser.ClassFieldContext): ClassFieldNode =
    ClassFieldNode(ctx.type().visit(), ctx.ID().visit(), ctx.span())

  override fun visitClassMethod(ctx: LatteParser.ClassMethodContext): FunDefNode =
    FunDefNode(ctx.type().visit(), ctx.ID().visit(), visitArg(ctx.arg()), visitBlock(ctx.block()), ctx.span())

  override fun visitArg(ctx: LatteParser.ArgContext?) = ArgsNode(
    ctx?.type().orEmpty().zip(ctx?.ID().orEmpty()).map { (type, ident) -> ArgNode(type.visit(), ident.visit()) },
    ctx?.span()
  )

  override fun visitBlock(ctx: LatteParser.BlockContext) =
    BlockNode(ctx.stmt().map { it.visit() }.filterTo(ArrayList()) { it != EmptyStmtNode }, ctx.span())

  override fun visitEmpty(ctx: LatteParser.EmptyContext) = EmptyStmtNode
  override fun visitBlockStmt(ctx: LatteParser.BlockStmtContext) = BlockStmtNode(visitBlock(ctx.block()), ctx.span())
  override fun visitAss(ctx: LatteParser.AssContext) = AssStmtNode(ctx.ID().visit(), ctx.expr().visit(), ctx.span())
  override fun visitIncr(ctx: LatteParser.IncrContext) = IncrStmtNode(ctx.ID().visit(), ctx.span())
  override fun visitDecr(ctx: LatteParser.DecrContext) = DecrStmtNode(ctx.ID().visit(), ctx.span())
  override fun visitRet(ctx: LatteParser.RetContext) = RetStmtNode(ctx.expr().visit(), ctx.span())
  override fun visitVRet(ctx: LatteParser.VRetContext) = VRetStmtNode(ctx.span())
  override fun visitSExp(ctx: LatteParser.SExpContext) = ExprStmtNode(ctx.expr().visit(), ctx.span())
  override fun visitRefAss(ctx: LatteParser.RefAssContext) =
    ctx.expr().map { it.visit() }
      .let { expr -> RefAssStmtNode(expr.first(), ctx.ID().visit(), expr.last(), ctx.span()) }

  override fun visitDecl(ctx: LatteParser.DeclContext) =
    DeclStmtNode(ctx.type().visit(), ctx.item().map { visitItem(it) }, ctx.span())

  override fun visitCond(ctx: LatteParser.CondContext) = when (val expr = ctx.expr().visit()) {
    is BoolExprNode -> if (!expr.value) EmptyStmtNode else ctx.stmt().visit()
    else -> CondStmtNode(expr, ctx.stmt().visit(), ctx.span())
  }

  override fun visitWhile(ctx: LatteParser.WhileContext) = when (val expr = ctx.expr().visit()) {
    is BoolExprNode -> if (!expr.value) EmptyStmtNode else WhileStmtNode(expr, ctx.stmt().visit(), ctx.span())
    else -> WhileStmtNode(expr, ctx.stmt().visit(), ctx.span())
  }

  override fun visitCondElse(ctx: LatteParser.CondElseContext) = when (val expr = ctx.expr().visit()) {
    is BoolExprNode -> if (expr.value) ctx.stmt(0).visit() else ctx.stmt(1).visit()
    else -> CondElseStmtNode(expr, ctx.stmt(0).visit(), ctx.stmt(1).visit(), ctx.span())
  }

  override fun visitItem(ctx: LatteParser.ItemContext) =
    ctx.expr()?.visit()?.let { InitItemNode(ctx.ID().visit(), it, ctx.span()) } ?: NotInitItemNode(
      ctx.ID().visit(),
      ctx.span()
    )

  override fun visitEFunCall(ctx: LatteParser.EFunCallContext) =
    FunCallExprNode(ctx.ID().visit(), ctx.expr().orEmpty().map { it.visit() }, ctx.span())

  override fun visitERelOp(ctx: LatteParser.ERelOpContext) =
    BinOpExprNode(ctx.expr(0).visit(), ctx.relOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEAddOp(ctx: LatteParser.EAddOpContext) =
    BinOpExprNode(ctx.expr(0).visit(), ctx.addOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEMulOp(ctx: LatteParser.EMulOpContext) =
    BinOpExprNode(ctx.expr(0).visit(), ctx.mulOp().visit(), ctx.expr(1).visit(), ctx.span())

  override fun visitEAnd(ctx: LatteParser.EAndContext) =
    BinOpExprNode(ctx.expr(0).visit(), BooleanOp.AND, ctx.expr(1).visit(), ctx.span())

  override fun visitEOr(ctx: LatteParser.EOrContext) =
    BinOpExprNode(ctx.expr(0).visit(), BooleanOp.OR, ctx.expr(1).visit(), ctx.span())

  override fun visitEClassField(ctx: LatteParser.EClassFieldContext): FieldExprNode =
    FieldExprNode(ctx.expr().visit(), ctx.ID().visit(), ctx.span())

  override fun visitECast(ctx: LatteParser.ECastContext): CastExprNode =
    CastExprNode(ctx.type().visit(), ctx.expr().visit(), ctx.span())

  override fun visitEStr(ctx: LatteParser.EStrContext) =
    StringExprNode(ctx.STR().text.run { substring(1, length - 1) }, ctx.span())

  override fun visitENull(ctx: LatteParser.ENullContext): NullExprNode = NullExprNode(ctx.span())
  override fun visitEThis(ctx: LatteParser.EThisContext): ThisExprNode = ThisExprNode(ctx.span())
  override fun visitEId(ctx: LatteParser.EIdContext) = IdentExprNode(ctx.ID().visit(), ctx.span())
  override fun visitEUnOp(ctx: LatteParser.EUnOpContext) =
    UnOpExprNode(ctx.unOp().visit(), ctx.expr().visit(), ctx.span())

  override fun visitEParen(ctx: LatteParser.EParenContext) = ctx.expr().visit()
  override fun visitEInt(ctx: LatteParser.EIntContext) = IntExprNode(ctx.INT().visit(), ctx.span())
  override fun visitETrue(ctx: LatteParser.ETrueContext) = BoolExprNode(true, ctx.span())
  override fun visitEFalse(ctx: LatteParser.EFalseContext) = BoolExprNode(false, ctx.span())
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

  private fun LatteParser.StmtContext.visit() = visitReturning<StmtNode>()
  private fun LatteParser.ExprContext.visit() = visitReturning<ExprNode>()
  private fun LatteParser.TypeContext.visit() = visitReturning<Type>()
  private fun LatteParser.AddOpContext.visit() = visitReturning<NumOp>()
  private fun LatteParser.MulOpContext.visit() = visitReturning<NumOp>()
  private fun LatteParser.RelOpContext.visit() = visitReturning<RelOp>()
  private fun LatteParser.UnOpContext.visit() = visitReturning<UnOp>()
  private fun LatteParser.TopDefContext.visit() = visitReturning<TopDefNode>()

  private inline fun <reified T> ParserRuleContext.visitReturning() = accept(this@AstVisitor) as T
  private fun Token.loc() = FileLocation(line, charPositionInLine)
  private fun ParserRuleContext.span() = Span(start.loc(), stop.loc())
  private fun TerminalNode.visit() = text
}
