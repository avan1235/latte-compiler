package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*

data class Label(val name: String)
sealed interface ValueHolder {
  val type: Type
}

sealed class ConstValue(override val type: Type) : ValueHolder
data class IntConstValue(val int: Int) : ConstValue(IntType)
data class BooleanConstValue(val bool: Boolean) : ConstValue(BooleanType)
data class StringConstValue(val label: Label, val str: String) : ConstValue(StringType)

sealed interface MemoryLoc : ValueHolder {
  val name: String
  val idx: Int
}

data class LocalValue(override val name: String, override val idx: Int, override val type: Type) : MemoryLoc
data class ArgValue(override val name: String, override val idx: Int, override val type: Type) : MemoryLoc
data class TempValue(override val name: String, override val idx: Int, override val type: Type) : MemoryLoc

sealed interface Quadruple
data class BinOpQ(val to: MemoryLoc, val left: MemoryLoc, val op: BinOp, val right: MemoryLoc) : Quadruple
data class UnOpQ(val to: MemoryLoc, val op: UnOp, val from: MemoryLoc) : Quadruple
data class AssignQ(val to: MemoryLoc, val from: ValueHolder) : Quadruple
data class JumpQ(val label: Label) : Quadruple
data class CondJumpQ(val cond: MemoryLoc, val onTrue: Label) : Quadruple
data class BiCondJumpQ(val left: MemoryLoc, val op: RelOp, val right: MemoryLoc, val onTrue: Label) : Quadruple
data class RetQ(val valueHolder: MemoryLoc? = null) : Quadruple
data class FunCallQ(val to: MemoryLoc, val label: Label, val args: List<ValueHolder>) : Quadruple
data class CodeFunLabelQ(override val label: Label, val args: List<ArgValue>) : LabelQuadruple
data class CodeLabelQ(override val label: Label) : LabelQuadruple
sealed interface LabelQuadruple : Quadruple {
  val label: Label
}
