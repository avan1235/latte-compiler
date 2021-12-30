package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*

interface Named {
  val name: String
}

data class Label(override val name: String) : Named
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
sealed interface JumpingQ : Quadruple {
  val toLabel: Label?
}

sealed interface LabelQ : Quadruple {
  val label: Label
}

data class BinOpQ(val to: MemoryLoc, val left: MemoryLoc, val op: NumOp, val right: ValueHolder) : Quadruple
data class UnOpQ(val to: MemoryLoc, val op: UnOp, val from: MemoryLoc) : Quadruple
data class AssignQ(val to: MemoryLoc, val from: ValueHolder) : Quadruple
data class IncQ(val toFrom: MemoryLoc) : Quadruple
data class DecQ(val toFrom: MemoryLoc) : Quadruple
data class FunCallQ(val to: MemoryLoc, val label: Label, val args: List<ValueHolder>) : Quadruple

data class FunCodeLabelQ(override val label: Label, val args: List<ArgValue>) : LabelQ
data class CodeLabelQ(override val label: Label) : LabelQ

data class CondJumpQ(val cond: MemoryLoc, override val toLabel: Label) : JumpingQ
data class BiCondJumpQ(val left: MemoryLoc, val op: RelOp, val right: ValueHolder, override val toLabel: Label) :
  JumpingQ

data class JumpQ(override val toLabel: Label) : JumpingQ
data class RetQ(val value: ValueHolder? = null) : JumpingQ {
  override val toLabel: Label? = null
}

