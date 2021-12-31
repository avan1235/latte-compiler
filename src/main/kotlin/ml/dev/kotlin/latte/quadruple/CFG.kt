package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*
import java.util.*

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
    val visited = starts.flatMapTo(HashSet()) { reachable(from = it) }
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

  private fun blockDefinedVars(label: Label): Set<MemoryLoc> =
    byName[label]?.statements?.mapNotNullTo(HashSet()) { it.definedVar() } ?: emptySet()

  /**
   * Based on CS553 "Lecture Control-Flow, Dominators, Loop Detection, and SSA"
   * from Colorado State University by Louis-Noel Pouchet
   * at https://www.cs.colostate.edu/~cs553/ClassNotes/lecture09-control-dominators.ppt.pdf page 15th
   */
  private fun insertPhonyIn(function: Label, dominance: Dominance<Label>) {
    val functionBlocks = reachable(from = function)
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
          byName[d]?.let { it += Phony(v) }
          if (d in visited) continue
          workList += d
          visited += d
        }
      }
    }
  }

  /**
   * Based on CS553 "Lecture Control-Flow, Dominators, Loop Detection, and SSA"
   * from Colorado State University by Louis-Noel Pouchet
   * at https://www.cs.colostate.edu/~cs553/ClassNotes/lecture09-control-dominators.ppt.pdf page 17th
   */
  private fun renameVariablesIn(function: Label, dominance: Dominance<Label>) {
    val dominanceTree = dominance.dominanceTree
    val counters = MutableDefaultMap<MemoryLoc, Int>({ 0 })
    val stacks = MutableDefaultMap<MemoryLoc, Stack<Int>>({ Stack() })
    val renamed = HashSet<Label>()

    val increaseIdx = { v: MemoryLoc ->
      val i = counters[v]
      stacks[v].push(i)
      counters[v] = i + 1
    }
    val decreaseIdx = { v: MemoryLoc -> stacks[v].pop() }
    val currIndex = { v: MemoryLoc -> stacks[v].let { if (it.isNotEmpty()) it.peek() else null } }

    fun rename(b: Label) {
      if (b in renamed) return
      renamed += b

      val basicBlock = byName[b] ?: return
      basicBlock.phony.forEach { it.renameDefinition(currIndex, increaseIdx) }
      basicBlock.mapStatements { stmt -> if (stmt is Rename) stmt.rename(currIndex, increaseIdx) else stmt }
      successors(b).forEach { succ -> byName[succ]?.phony?.forEach { it.renamePathUsage(from = b, currIndex) } }
      dominanceTree.successors(b).forEach { succ -> rename(succ) }
      basicBlock.phony.forEach { phi -> decreaseIdx(phi.to.original!!) }
      basicBlock.statements.forEach { stmt -> stmt.definedVar()?.let { decreaseIdx(it.original!!) } }
    }
    rename(function)
  }

  private fun removeNotUsablePhony() {
    byName.values.forEach { b ->
      val predecessors = predecessors(b.label).size
      b.filterPhony { it.from.size == predecessors }
    }
  }

  private fun transformToSSA() {
    fun toSSA(function: Label) {
      val dominance = Dominance(function, this)
      insertPhonyIn(function, dominance)
      renameVariablesIn(function, dominance)
    }
    starts.forEach { toSSA(it) }
    removeNotUsablePhony()
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
      transformToSSA()
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
