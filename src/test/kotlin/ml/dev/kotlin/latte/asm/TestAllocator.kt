package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.DEFAULT_ALLOCATOR_STRATEGY
import ml.dev.kotlin.latte.quadruple.LocalValue
import ml.dev.kotlin.latte.quadruple.VirtualReg
import java.util.*

internal enum class TestAllocator(val strategy: AllocatorStrategyProducer) {
  DEFAULT(DEFAULT_ALLOCATOR_STRATEGY),
  FIRST({ analysis, manager ->
    object : AllocatorStrategy(analysis, manager) {
      override fun selectToSplit(withEdges: TreeMap<Int, HashSet<LocalValue>>): LocalValue =
        withEdges.values.flatten().first()

      override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<LocalValue, VarLoc>): Reg =
        available.first()
    }
  }),
  RANDOM({ analysis, manager ->
    object : AllocatorStrategy(analysis, manager) {
      override fun selectToSplit(withEdges: TreeMap<Int, HashSet<LocalValue>>): LocalValue =
        withEdges.values.flatten().random()

      override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<LocalValue, VarLoc>): Reg =
        available.random()
    }
  }),
  LAST({ analysis, manager ->
    object : AllocatorStrategy(analysis, manager) {

      override fun selectToSplit(withEdges: TreeMap<Int, HashSet<LocalValue>>): LocalValue =
        withEdges.values.flatten().last()

      override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<LocalValue, VarLoc>): Reg =
        available.last()
    }
  });

  override fun toString(): String = "${name.lowercase()} memory allocator"
}
