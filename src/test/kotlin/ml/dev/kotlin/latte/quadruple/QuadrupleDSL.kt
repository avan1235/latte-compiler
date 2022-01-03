package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.IntType
import ml.dev.kotlin.latte.syntax.NumOp


internal class QuadrupleDSL {

  private val _statements = ArrayList<Quadruple>()
  val statements: List<Quadruple> get() = _statements

  infix fun VirtualReg.eq(other: ValueHolder) {
    _statements += AssignQ(this, other)
  }

  infix fun VirtualReg.eq(operands: Operands) {
    _statements += BinOpQ(this, operands.left, operands.op, operands.right)
  }
}

internal fun quadrupleDsl(instructions: QuadrupleDSL.() -> Unit): QuadrupleDSL =
  QuadrupleDSL().apply(instructions)

internal operator fun VirtualReg.plus(other: ValueHolder): Operands = Operands(this, NumOp.PLUS, other)
internal operator fun VirtualReg.minus(other: ValueHolder): Operands = Operands(this, NumOp.MINUS, other)
internal operator fun VirtualReg.times(other: ValueHolder): Operands = Operands(this, NumOp.TIMES, other)
internal operator fun VirtualReg.div(other: ValueHolder): Operands = Operands(this, NumOp.DIVIDE, other)
internal operator fun VirtualReg.rem(other: ValueHolder): Operands = Operands(this, NumOp.MOD, other)

internal data class Operands(val left: VirtualReg, val op: NumOp, val right: ValueHolder)

internal fun v(name: String): LocalValue = LocalValue(name, 0, IntType)
