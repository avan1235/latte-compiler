package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.*
import java.util.*
import kotlin.collections.ArrayDeque


class FunctionCFG private constructor(
  private val start: Label,
  private val jumpPred: MutableDefaultMap<Label, LinkedHashSet<Label>> = MutableDefaultMap({ LinkedHashSet() }),
  private val byName: LinkedHashMap<Label, BasicBlock> = LinkedHashMap(),
) : DirectedGraph<Label> {
  val block: Map<Label, BasicBlock> get() = byName

  fun orderedBlocks(): List<BasicBlock> = byName.values.toList()

  fun removeNotReachableBlocks() {
    val visited = reachable(from = start)
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

  fun transformToSSA() {
    val dominance = Dominance(start, this)
    insertPhony(dominance)
    renameVariables(dominance)
  }

  fun transformFromSSA() {
    byName.values.toHashSet().forEach { b ->
      b.phony.forEach { phi ->
        phi.from.entries.forEach { (from, name) ->
          val block = byName[from] ?: err("Unknown block defined in $phi")
          block += AssignQ(phi.to, name)
        }
      }
      b.cleanPhony()
    }
  }

  private fun addBlock(block: BasicBlock) {
    if (block.label in byName) err("CFG already contains a BasicBlock labeled ${block.label}")
    byName[block.label] = block
    block.jumpQ?.toLabel?.let { to -> jumpPred[to] += block.label }
  }

  private fun addLinearJump(from: BasicBlock, to: BasicBlock) {
    if (to.isStart) return
    if (from.jumpQ is JumpQ || from.jumpQ is RetQ) return
    from.linSucc = to
    to.linPred = from
  }

  private fun scanVariables(): Variables {
    val functionBlocks = reachable(from = start)
    val inBlocks = MutableDefaultMap<VirtualReg, MutableSet<Label>>({ HashSet() })
    val globals = HashSet<VirtualReg>()
    for (b in functionBlocks) {
      val varKill = HashSet<VirtualReg>()
      byName[b]?.statementsWithPhony?.forEach { stmt ->
        stmt.usedVars().filterNot { it in varKill }.forEach { globals += it }
        stmt.definedVars().onEach { varKill += it }.forEach { inBlocks[it] += b }
      }
    }
    return Variables(inBlocks, globals)
  }

  /**
   * Based on CS553 "Lecture Control-Flow, Dominators, Loop Detection, and SSA"
   * from Colorado State University by Louis-Noel Pouchet
   * at https://www.cs.colostate.edu/~cs553/ClassNotes/lecture09-control-dominators.ppt.pdf page 15th
   */
  private fun insertPhony(dominance: Dominance<Label>) {
    val (inBlocks, defined) = scanVariables()
    for (v in defined) {
      val (workList, visited, phiInserted) = get(count = 3) { HashSet<Label>() }
      inBlocks[v].also { workList += it }.also { visited += it }
      while (workList.isNotEmpty()) {
        val n = workList.first().also { workList -= it }
        for (d in dominance.frontiers(n)) {
          if (d in phiInserted) continue
          phiInserted += d
          byName[d]?.let { it += PhonyQ(v) }
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
  private fun renameVariables(dominance: Dominance<Label>) {
    val dominanceTree = dominance.dominanceTree
    val counters = MutableDefaultMap<VirtualReg, Int>({ FIRST_DEFINITION_COUNT })
    val stacks = MutableDefaultMap<VirtualReg, ArrayDeque<Int>>({ ArrayDeque() })
    val renamed = HashSet<Label>()

    val increaseIdx = { v: VirtualReg ->
      val i = counters[v]
      stacks[v] += i
      counters[v] = i + 1
    }
    val decreaseIdx = { v: VirtualReg -> stacks[v].removeLast() }
    val currIndex = { v: VirtualReg -> stacks[v].let { if (it.isNotEmpty()) it.last() else null } }

    fun rename(b: Label) {
      if (b in renamed) return
      renamed += b

      val basicBlock = byName[b] ?: return
      basicBlock.phony.forEach { it.renameDefinition(currIndex, increaseIdx) }
      basicBlock.mapStatements { _, stmt -> stmt.rename(currIndex, increaseIdx) }
      successors(b).forEach { succ -> byName[succ]?.phony?.forEach { it.renamePathUsage(from = b, currIndex) } }
      dominanceTree.successors(b).forEach { succ -> rename(succ) }
      basicBlock.phony.forEach { phi -> phi.to.original?.let(decreaseIdx) }
      basicBlock.statements.forEach { stmt -> stmt.definedVars().forEach { it.original?.let(decreaseIdx) } }
    }
    rename(start)
  }

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
    fun Iterable<BasicBlock>.buildFunctionCFG(label: Label): FunctionCFG = FunctionCFG(label).apply {
      onEach { addBlock(it) }.windowed(2).forEach { (from, to) -> addLinearJump(from, to) }
    }
  }
}

const val FIRST_DEFINITION_COUNT: Int = 0

private data class Variables(val inBlocks: DefaultMap<VirtualReg, Set<Label>>, val defined: Set<VirtualReg>)

private fun err(message: String): Nothing = throw IRException(message.msg)
