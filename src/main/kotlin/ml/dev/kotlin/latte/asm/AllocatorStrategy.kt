package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.Type
import ml.dev.kotlin.latte.util.GraphColoringStrategy
import java.util.*

open class AllocatorStrategy(
  private val analysis: FlowAnalysis,
  private val memoryManager: MemoryManager,
) : GraphColoringStrategy<VirtualReg, VarLoc, Reg, Mem> {

  private val aliveOnSomeFunctionCall: Set<VirtualReg> = analysis.statements.asSequence().withIndex()
    .filter { it.value is FunCallQ }.map { it.index }
    .flatMapTo(HashSet()) { analysis.aliveOver[it] }

  protected open fun selectToSplit(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg {
    val descendingCounts = withEdges.descendingKeySet()
    return descendingCounts.firstNotNullOfOrNull { withEdges[it]?.intersect(aliveOnSomeFunctionCall)?.firstOrNull() }
      ?: descendingCounts.firstNotNullOfOrNull { withEdges[it]?.firstOrNull() }
      ?: withEdges.values.last().first()
  }

  protected open fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<VirtualReg, VarLoc>): Reg {
    val regCounts = coloring.values.filterIsInstance<Reg>().groupingBy { it }.eachCountTo(EnumMap(Reg::class.java))
    val reg = available.minByOrNull { regCounts[it] ?: 0 } ?: available.first()
    if (virtualReg is ArgValue) memoryManager.moveArgToReg(virtualReg, reg)
    return reg
  }

  protected open fun assignDefault(register: VirtualReg): Mem = when (register) {
    is LocalValue -> memoryManager.reserveLocal(register.type)
    is ArgValue -> memoryManager.reserveArg(register)
  }

  final override fun extraColor(v: VirtualReg): Mem = assignDefault(v)

  final override fun spillSelectHeuristics(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg =
    selectToSplit(withEdges)

  final override fun colorSelectHeuristics(
    node: VirtualReg,
    available: Set<Reg>,
    coloring: Map<VirtualReg, VarLoc>
  ): Reg = selectColor(node, available, coloring)
}

interface MemoryManager {
  fun reserveLocal(type: Type): Loc
  fun reserveArg(register: ArgValue): Arg
  fun moveArgToReg(arg: ArgValue, reg: Reg)
}
