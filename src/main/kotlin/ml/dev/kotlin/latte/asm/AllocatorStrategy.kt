package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.FlowAnalysis
import ml.dev.kotlin.latte.quadruple.FunCallQ
import ml.dev.kotlin.latte.quadruple.LocalValue
import ml.dev.kotlin.latte.quadruple.VirtualReg
import ml.dev.kotlin.latte.util.GraphColoringStrategy
import java.util.*

open class AllocatorStrategy(
  private val analysis: FlowAnalysis,
  private val memoryManager: MemoryManager,
) : GraphColoringStrategy<LocalValue, VarLoc, Reg, Loc> {

  private val aliveOnSomeFunctionCall: Set<LocalValue> = analysis.statements.asSequence().withIndex()
    .filter { it.value is FunCallQ }.map { it.index }
    .flatMap { analysis.aliveOver[it] }
    .filterIsInstanceTo(HashSet())

  protected open fun selectToSplit(withEdges: TreeMap<Int, HashSet<LocalValue>>): LocalValue {
    val descendingCounts = withEdges.descendingKeySet()
    return descendingCounts.firstNotNullOfOrNull { withEdges[it]?.intersect(aliveOnSomeFunctionCall)?.firstOrNull() }
      ?: descendingCounts.firstNotNullOfOrNull { withEdges[it]?.firstOrNull() }
      ?: withEdges.values.last().first()
  }

  protected open fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<LocalValue, VarLoc>): Reg {
    val regCounts = coloring.values.filterIsInstance<Reg>().groupingBy { it }.eachCountTo(EnumMap(Reg::class.java))
    return available.minByOrNull { regCounts[it] ?: 0 } ?: available.first()
  }

  private fun assignDefault(register: LocalValue): Loc = memoryManager.reserveLocal(register.id, register.type)

  final override fun extraColor(v: LocalValue): Loc = assignDefault(v)

  final override fun spillSelectHeuristics(withEdges: TreeMap<Int, HashSet<LocalValue>>): LocalValue =
    selectToSplit(withEdges)

  final override fun colorSelectHeuristics(
    node: LocalValue,
    available: Set<Reg>,
    coloring: Map<LocalValue, VarLoc>
  ): Reg = selectColor(node, available, coloring)
}

