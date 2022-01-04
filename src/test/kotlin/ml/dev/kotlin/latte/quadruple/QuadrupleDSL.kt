package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.NumOp


internal class QuadrupleDSL {

  private val _statements = ArrayList<Quadruple>()
  val statements: List<Quadruple> get() = _statements

  infix fun VirtualReg.eq(other: ValueHolder) {
    _statements += AssignQ(this, other)
  }

  infix fun VirtualReg.eq(operands: NumOperands) {
    _statements += BinOpQ(this, operands.left, operands.op, operands.right)
  }
}

internal fun quadrupleDsl(instructions: QuadrupleDSL.() -> Unit): QuadrupleDSL =
  QuadrupleDSL().apply(instructions)

internal operator fun VirtualReg.plus(other: ValueHolder): NumOperands = NumOperands(this, NumOp.PLUS, other)
internal operator fun VirtualReg.minus(other: ValueHolder): NumOperands = NumOperands(this, NumOp.MINUS, other)
internal operator fun VirtualReg.times(other: ValueHolder): NumOperands = NumOperands(this, NumOp.TIMES, other)
internal operator fun VirtualReg.div(other: ValueHolder): NumOperands = NumOperands(this, NumOp.DIVIDE, other)
internal operator fun VirtualReg.rem(other: ValueHolder): NumOperands = NumOperands(this, NumOp.MOD, other)

internal data class NumOperands(val left: VirtualReg, val op: NumOp, val right: ValueHolder)

internal fun v(name: String): LocalValue = LocalValue(name, IntType)
