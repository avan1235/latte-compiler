package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.BinOp
import ml.dev.kotlin.latte.syntax.RelOp
import ml.dev.kotlin.latte.syntax.UnOp

@JvmInline
value class CodeLabel(val name: String)

inline val String.label get() = CodeLabel(this)

sealed interface Quadruple

data class BinOpQ(val to: CodeLabel, val left: CodeLabel, val op: BinOp, val right: CodeLabel) : Quadruple
data class UnOpQ(val to: CodeLabel, val op: UnOp, val from: CodeLabel) : Quadruple
data class CopyQ(val to: CodeLabel, val from: CodeLabel) : Quadruple
data class JumpQ(val label: CodeLabel) : Quadruple
data class RelCondJumpQ(val left: CodeLabel, val op: RelOp, val right: CodeLabel, val label: CodeLabel) : Quadruple
data class BoolCondJumpQ(val cond: CodeLabel, val label: CodeLabel) : Quadruple
data class ReturnQ(val CodeLabel: CodeLabel? = null) : Quadruple
data class FunctionCall(val to: CodeLabel, val funName: CodeLabel, val args: List<CodeLabel>) : Quadruple

