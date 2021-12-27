package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.MutableDefaultMap

data class SimpleBlock(
  val label: CodeLabel,
  val instructions: List<Quadruple>,
)

class ControlFlowGraph(
  private val adj: MutableDefaultMap<CodeLabel, MutableSet<CodeLabel>> = MutableDefaultMap({ hashSetOf() }),
  private val byName: MutableMap<CodeLabel, SimpleBlock> = hashMapOf()
)
