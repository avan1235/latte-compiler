package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*

data class ControlFlowGraph(
  private val jumpPred: MutableDefaultMap<Label, LinkedHashSet<Label>> = MutableDefaultMap({ LinkedHashSet() }),
  private val byName: MutableMap<Label, BasicBlock> = LinkedHashMap(),
  private val starts: MutableSet<Label> = HashSet(),
) : Graph<Label> {
  fun orderedBlocks(): List<BasicBlock> = byName.values.toList()

  private fun addBlock(block: BasicBlock) {
    if (block.label in byName) err("CFG already contains a BasicBlock labeled ${block.label}")
    byName[block.label] = block
    block.jumpQ?.toLabel?.let { to -> jumpPred[to] += block.label }
    block.takeIf { it.isStart }?.label?.let { starts += it }
  }

  private fun addLinearJump(from: BasicBlock, to: BasicBlock) {
    if (to.isStart) return
    if (from.jumpQ is JumpQ || from.jumpQ is RetQ) return
    from.linSucc = to
    to.linPred = from
  }

  private fun removeNotReachableBlocks() {
    val visited = starts.flatMapTo(HashSet()) { reachableBlocks(it) }
    val notVisited = byName.keys.toHashSet() - visited
    notVisited.forEach { block ->
      val linPred = byName[block]?.linPred
      val linSucc = byName[block]?.linSucc
      linPred?.linSucc = linSucc
      linSucc?.linPred = linPred
      byName[block]?.jumpQ?.toLabel?.let { jumpPred[it] -= block }
      byName -= block
    }
  }

  private fun reachableBlocks(from: Label): Set<Label> {
    val visited = HashSet<Label>()
    val queue = ArrayDeque<Label>()
    tailrec fun go(from: Label) {
      visited += from
      successors(from).forEach { if (it !in visited) queue += it }
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
  override fun predecessors(v: Label): Set<Label> = buildSet {
    addAll(jumpPred[v])
    byName[v]?.linPred?.label?.let { add(it) }
  }

  override fun successors(v: Label): Set<Label> = buildSet {
    byName[v]?.linSucc?.label?.let { add(it) }
    byName[v]?.jumpQ?.toLabel?.let { add(it) }
  }

  companion object {
    fun Iterable<Quadruple>.buildCFG(labelGenerator: () -> CodeLabelQ): ControlFlowGraph = ControlFlowGraph().apply {
      splitAt(first = { it is LabelQ }, last = { it is JumpingQ })
        .map { it.toBasicBlock(labelGenerator) }
        .onEach { addBlock(it) }
        .windowed(2).forEach { (from, to) -> addLinearJump(from, to) }
      removeNotReachableBlocks()
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
