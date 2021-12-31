package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString

data class BasicBlock(
  val instructions: List<Quadruple>,
  val isStart: Boolean,
  val label: Label,
  val jumpQ: JumpingQ?,
  val usedVars: Set<MemoryLoc>,
  val definedVars: Set<MemoryLoc>,
  var linPred: BasicBlock? = null,
  var linSucc: BasicBlock? = null,
  private val phony: MutableSet<Phony> = HashSet(),
)

fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
  val first = (firstOrNull() as? LabelQ) ?: labelGenerator().also { add(index = 0, it) }
  val jumpingIdx = indexOfFirst { it is JumpingQ }
  if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
  if (count { it is LabelQ } != 1) err("Basic block contains invalid labels: ${nlString()}")
  val jumping = lastOrNull() as? JumpingQ
  val usedVars = flatMapTo(HashSet()) { it.usedVars() }
  val definedVars = flatMapTo(HashSet()) { it.definedVars() }
  BasicBlock(this, first is FunCodeLabelQ, first.label, jumping, usedVars, definedVars)
}

private fun err(message: String): Nothing = throw IRException(message.msg)
