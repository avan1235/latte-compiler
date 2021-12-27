package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.BinOp
import ml.dev.kotlin.latte.syntax.RelOp
import ml.dev.kotlin.latte.syntax.UnOp

sealed interface ValueHolder
data class Label(val name: String) : ValueHolder
data class BooleanValue(val raw: Boolean) : ValueHolder
data class IntValue(val raw: String) : ValueHolder

inline val String.label get() = Label(this)

sealed interface Quadruple

data class BinOpQ(val to: Label, val left: Label, val op: BinOp, val right: Label) : Quadruple
data class UnOpQ(val to: Label, val op: UnOp, val from: Label) : Quadruple
data class AssignQ(val to: Label, val from: ValueHolder) : Quadruple
data class IncQ(val label: Label) : Quadruple
data class DecQ(val label: Label) : Quadruple
data class JumpQ(val label: Label) : Quadruple
data class RelCondJumpQ(val left: Label, val op: RelOp, val right: Label, val goto: Label) : Quadruple
data class BoolCondJumpQ(val cond: Label, val goto: Label) : Quadruple
data class RetQ(val valueHolder: Label? = null) : Quadruple
data class FunCallQ(val to: Label, val funName: Label, val args: List<Label>) : Quadruple

