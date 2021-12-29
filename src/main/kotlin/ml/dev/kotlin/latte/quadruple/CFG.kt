package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.MutableDefaultMap
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.splitAt
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.LinkedHashMap

data class ControlFlowGraph(
  private val jumpFrom: MutableDefaultMap<Label, MutableSet<Label>> = MutableDefaultMap({ hashSetOf() }),
  private val jumpTo: MutableDefaultMap<Label, MutableSet<Label>> = MutableDefaultMap({ hashSetOf() }),
  private val byName: HashMap<Label, BasicBlock> = LinkedHashMap(),
  private val starts: MutableSet<Label> = HashSet(),
) {
  fun orderedBlocks(): List<BasicBlock> = byName.values.toList()

  private fun addBlock(block: BasicBlock) {
    if (block.label in byName) err("CFG already contains a BasicBlock labeled ${block.label}")
    byName[block.label] = block
    block.jumpQ?.toLabel?.let { toLabel ->
      jumpFrom[block.label] += toLabel
      jumpTo[toLabel] += block.label
    }
    block.takeIf { it.isStart }?.label?.let { starts += it }
  }

  private fun addJump(from: BasicBlock, to: BasicBlock) {
    if (to.isStart) return
    if (from.jumpQ is JumpQ || from.jumpQ is RetQ) return
    jumpFrom[from.label] += to.label
    jumpTo[to.label] += from.label
  }

  private fun removeNotReachableBlocks() {
    val visited = starts.flatMapTo(HashSet()) { reachableFrom(it) }
    val notVisited = byName.keys.toHashSet() - visited
    jumpFrom -= notVisited
    byName -= notVisited
  }

  private fun reachableFrom(label: Label): Set<Label> {
    val visited = HashSet<Label>()
    val queue = ArrayDeque<Label>()
    tailrec fun go(from: Label) {
      visited += from
      jumpFrom[from].forEach { if (it !in visited) queue += it }
      return go(queue.removeLastOrNull() ?: return)
    }
    return visited.also { go(label) }
  }

  private fun transformToSSA() {
    val blockUsedVariables = byName.mapValues { it.value.usedVars }
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

private fun err(message: String): Nothing = throw IRException(message.msg)
