package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*

data class ControlFlowGraph(
  private val succ: MutableDefaultMap<Label, LinkedHashSet<Label>> = MutableDefaultMap({ LinkedHashSet() }),
  private val pred: MutableDefaultMap<Label, LinkedHashSet<Label>> = MutableDefaultMap({ LinkedHashSet() }),
  private val byName: HashMap<Label, BasicBlock> = LinkedHashMap(),
  private val starts: MutableSet<Label> = HashSet(),
) : Graph<Label> {
  fun orderedBlocks(): List<BasicBlock> = byName.values.toList()

  private fun addBlock(block: BasicBlock) {
    if (block.label in byName) err("CFG already contains a BasicBlock labeled ${block.label}")
    byName[block.label] = block
    block.jumpQ?.toLabel?.let { toLabel ->
      succ[block.label] += toLabel
      pred[toLabel] += block.label
    }
    block.takeIf { it.isStart }?.label?.let { starts += it }
  }

  private fun addJump(from: BasicBlock, to: BasicBlock) {
    if (to.isStart) return
    if (from.jumpQ is JumpQ || from.jumpQ is RetQ) return
    succ[from.label] += to.label
    pred[to.label] += from.label
  }

  private fun removeBlock(label: Label) {
    succ[label].forEach { pred[it] -= label }
    pred[label].forEach { succ[it] -= label }
    succ -= label
    pred -= label
    byName -= label
  }

  private fun removeNotReachableBlocks() {
    val visited = starts.flatMapTo(HashSet()) { reachableBlocks(it) }
    val notVisited = byName.keys.toHashSet() - visited
    notVisited.forEach { removeBlock(it) }
  }

  private fun reachableBlocks(from: Label): Set<Label> {
    val visited = HashSet<Label>()
    val queue = ArrayDeque<Label>()
    tailrec fun go(from: Label) {
      visited += from
      succ[from].forEach { if (it !in visited) queue += it }
      go(queue.removeLastOrNull() ?: return)
    }
    return visited.also { go(from) }
  }

  private fun blockUsedVars(label: Label): Set<MemoryLoc> = byName[label]?.usedVars ?: emptySet()
  private fun blockDefinedVars(label: Label): Set<MemoryLoc> = byName[label]?.definedVars ?: emptySet()

  private fun insertPhony() {
    for (f in starts) {
      val reachableFrom = reachableBlocks(f)
      val variableDefinedIn = MutableDefaultMap<MemoryLoc, HashSet<Label>>({ HashSet() }).also { definedIn ->
        reachableFrom.forEach { block -> blockDefinedVars(block).forEach { v -> definedIn[v] += block } }
      }
      val allVariables = reachableBlocks(f).flatMapTo(HashSet()) { blockDefinedVars(it) }
      val dominance = Dominance(f, this)
      for (v in allVariables) {
        val (workList, visited, alreadyHasPhiForV) = get(count = 3) { HashSet<Label>() }
        variableDefinedIn[v].also { workList += it }.also { visited += it }

        while (workList.isNotEmpty()) {
          val n = workList.first().also { workList -= it }
          for (d in dominance.frontiers(n)) {
            if (d !in alreadyHasPhiForV) {
              // TODO insert phi for v at d block
              alreadyHasPhiForV += d
              if (d !in visited) d.also { workList += it }.also { visited += it }
            }
          }
        }
      }
    }
  }

  override val size: Int get() = byName.size
  override val nodes: Set<Label> get() = byName.keys.toHashSet()
  override fun predecessors(v: Label): LinkedHashSet<Label> = pred[v]
  override fun successors(v: Label): LinkedHashSet<Label> = succ[v]

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
