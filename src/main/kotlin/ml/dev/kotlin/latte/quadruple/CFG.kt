package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*
import java.util.*
import kotlin.collections.ArrayDeque

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

  private fun blockDefinedVars(label: Label): Set<MemoryLoc> =
    byName[label]?.instructions?.mapNotNullTo(HashSet()) { it.definedVar() } ?: emptySet()

  private fun insertPhonyIn(functionBlocks: Set<Label>, dominance: Dominance<Label>) {
    val inBlocks = MutableDefaultMap<MemoryLoc, HashSet<Label>>({ HashSet() }).also { inBlocks ->
      functionBlocks.forEach { block -> blockDefinedVars(block).forEach { v -> inBlocks[v] += block } }
    }
    val functionVariables = inBlocks.keys.toHashSet()
    for (v in functionVariables) {
      val (workList, visited, phiInserted) = get(count = 3) { HashSet<Label>() }
      inBlocks[v].also { workList += it }.also { visited += it }
      while (workList.isNotEmpty()) {
        val n = workList.first().also { workList -= it }
        for (d in dominance.frontiers(n)) {
          if (d in phiInserted) continue
          phiInserted += d
          byName[d]?.phony?.add(Phony(v))
          if (d !in visited) d.also { workList += it }.also { visited += it }
        }
      }
    }
  }

  private fun renameVariablesIn(function: Label, functionBlocks: Set<Label>, dominanceTree: DominanceTree<Label>) {
    val succ = MutableDefaultMap<Label, Set<Label>>({ successors(it) })
    val counters = MutableDefaultMap<MemoryLoc, Int>({ 0 })
    val stacks = MutableDefaultMap<MemoryLoc, Stack<Int>>({ Stack() })
    val renamed = HashSet<Label>()

    val nameGen = fun(v: MemoryLoc) {
      val i = counters[v]
      stacks[v].push(i)
      counters[v] = i + 1
    }

    fun rename(block: Label) {
      if (block in renamed) return
      renamed += block

      val basicBlock = byName[block] ?: return

    }
  }

  private fun toSSA(function: Label) {
    val functionBlocks = reachableBlocks(function)
    val dominance = Dominance(function, this)
    insertPhonyIn(functionBlocks, dominance)
    renameVariablesIn(function, functionBlocks, dominance.dominanceTree)
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
      splitAt(first = { it is Labeled }, last = { it is Jumping })
        .map { it.toBasicBlock(labelGenerator) }
        .onEach { addBlock(it) }
        .windowed(2).forEach { (from, to) -> addLinearJump(from, to) }
      removeNotReachableBlocks()
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
