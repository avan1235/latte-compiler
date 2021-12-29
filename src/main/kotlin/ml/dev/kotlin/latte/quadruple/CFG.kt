package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.quadruple.BasicBlock.Companion.toBasicBlock
import ml.dev.kotlin.latte.util.*

data class ControlFlowGraph(
  private val adj: MutableDefaultMap<Label, MutableSet<Label>> = MutableDefaultMap({ hashSetOf() }),
  private val byName: LinkedHashMap<Label, BasicBlock> = LinkedHashMap(),
  private val starts: MutableSet<Label> = HashSet()
) {
  fun orderedBlocks(): List<BasicBlock> = byName.values.toList()

  private fun addBlock(block: BasicBlock) {
    if (block.label in byName) err("CFG already contains a BasicBlock labeled ${block.label}")
    byName[block.label] = block
    block.jumpQ?.toLabel?.let { adj[block.label] += it }
    block.takeIf { it.isStart }?.label?.let { starts += it }
  }

  private fun addJump(from: BasicBlock, to: BasicBlock) {
    if (to.isStart) return
    if (from.jumpQ is JumpQ || from.jumpQ is RetQ) return
    adj[from.label] += to.label
  }

  private fun removeNotReachableBlocks() {
    val visited = hashSetOf<Label>()
    val queue = ArrayDeque<Label>()
    tailrec fun go(from: Label) {
      visited += from
      adj[from].forEach { if (it !in visited) queue += it }
      return go(queue.removeLastOrNull() ?: return)
    }
    starts.forEach { go(it) }
    val notVisited = byName.keys.toHashSet() - visited
    adj -= notVisited
    byName -= notVisited
  }

  companion object {
    fun Iterable<Quadruple>.buildCFG(labelGenerator: () -> CodeLabelQ): ControlFlowGraph = ControlFlowGraph().apply {
      splitAt(first = { it is LabelQ }, last = { it is JumpingQ })
        .map { it.toBasicBlock(labelGenerator) }
        .onEach { addBlock(it) }
        .windowed(2).forEach { (from, to) -> addJump(from, to) }
      removeNotReachableBlocks()
    }
  }
}

data class BasicBlock(
  val instructions: List<Quadruple>,
  val isStart: Boolean,
  val label: Label,
  val jumpQ: JumpingQ?,
) {
  companion object {
    fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
      val first = (firstOrNull() as? LabelQ) ?: labelGenerator().also { add(index = 0, it) }
      val jumpingIdx = indexOfFirst { it is JumpingQ }
      if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
      if (count { it is LabelQ } != 1) err("Basic block contains invalid labels: ${nlString()}")
      val jumping = lastOrNull() as? JumpingQ
      BasicBlock(this, first is FunCodeLabelQ, first.label, jumping)
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
