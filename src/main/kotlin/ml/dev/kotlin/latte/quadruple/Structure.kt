package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.MutableDefaultMap

data class BasicBlock(
  val label: Label,
  val instructions: List<Quadruple>,
)

data class ControlFlowGraph(
  private val adj: MutableDefaultMap<Label, MutableSet<Label>> = MutableDefaultMap({ hashSetOf() }),
  private val byName: MutableMap<Label, BasicBlock> = hashMapOf()
)
