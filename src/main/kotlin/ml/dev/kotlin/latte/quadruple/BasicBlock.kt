package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString

data class BasicBlock(
  val instructions: List<Quadruple>,
  val isStart: Boolean,
  val label: Label,
  val jumpQ: JumpingQ?,
  val usedVars: Set<String>,
)

fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
  val first = (firstOrNull() as? LabelQ) ?: labelGenerator().also { add(index = 0, it) }
  val jumpingIdx = indexOfFirst { it is JumpingQ }
  if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
  if (count { it is LabelQ } != 1) err("Basic block contains invalid labels: ${nlString()}")
  val jumping = lastOrNull() as? JumpingQ
  val usedVars = flatMapTo(HashSet()) { it.usedVars() }
  BasicBlock(this, first is FunCodeLabelQ, first.label, jumping, usedVars)
}

private fun Quadruple.usedVars(): Sequence<String> = when (this) {
  is AssignQ -> sequenceOf(to, from as? MemoryLoc)
  is BinOpQ -> sequenceOf(to, left, right)
  is UnOpQ -> sequenceOf(to, from)
  is DecQ -> sequenceOf(toFrom)
  is IncQ -> sequenceOf(toFrom)
  is FunCallQ -> sequenceOf(to) + args.asSequence().filterIsInstance<MemoryLoc>()
  is BiCondJumpQ -> sequenceOf(left, right)
  is CondJumpQ -> sequenceOf(cond)
  is RetQ -> sequenceOf(value as? MemoryLoc)
  is JumpQ -> emptySequence()
  is CodeLabelQ -> emptySequence()
  is FunCodeLabelQ -> emptySequence()
}.filterNotNull().map { it.name }

private fun err(message: String): Nothing = throw IRException(message.msg)
