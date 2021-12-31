package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString

data class Phony(val to: MemoryLoc, val from: HashMap<Label, MemoryLoc> = HashMap())

data class BasicBlock(
  private val _instructions: MutableList<Quadruple>,
  val isStart: Boolean,
  val label: Label,
  val jumpQ: Jumping?,
  var linPred: BasicBlock? = null,
  var linSucc: BasicBlock? = null,
  val phony: HashSet<Phony> = HashSet(),
) {
  val instructions: List<Quadruple> = _instructions
  fun mapInstructions(f: (Quadruple) -> Quadruple) {
    val mappedInstructions = _instructions.map(f)
    _instructions.apply { clear() }.apply { addAll(mappedInstructions) }
  }
}

fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
  val first = (firstOrNull() as? Labeled) ?: labelGenerator().also { add(index = 0, it) }
  val jumpingIdx = indexOfFirst { it is Jumping }
  if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
  if (count { it is Labeled } != 1) err("Basic block contains invalid labels: ${nlString()}")
  val jumping = lastOrNull() as? Jumping
  BasicBlock(this, first is FunCodeLabelQ, first.label, jumping)
}

private fun err(message: String): Nothing = throw IRException(message.msg)
