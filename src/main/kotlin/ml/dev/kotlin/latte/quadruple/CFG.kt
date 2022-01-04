package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.quadruple.BasicBlock.Companion.toBasicBlock
import ml.dev.kotlin.latte.quadruple.FunctionCFG.Companion.buildFunctionCFG
import ml.dev.kotlin.latte.util.splitAt


data class ControlFlowGraph(
  private val starts: Map<Label, FunctionCFG>,
) {
  fun orderedBlocks(): List<BasicBlock> = starts.values.flatMap { it.orderedBlocks() }
  fun removeNotReachableBlocks(): Unit = forEachStart { removeNotReachableBlocks() }
  fun transformToSSA(): Unit = forEachStart { transformToSSA() }
  fun transformFromSSA(): Unit = forEachStart { transformFromSSA() }

  private inline fun forEachStart(action: FunctionCFG.() -> Unit): Unit = starts.values.forEach(action)

  companion object {
    fun Iterable<Quadruple>.buildCFG(labelGenerator: () -> Label): ControlFlowGraph {
      val funCFGs = splitAt(first = { it is Labeled }, last = { it is Jumping })
        .map { it.toBasicBlock { CodeLabelQ(labelGenerator()) } }.asIterable()
        .splitAt(first = { it.isStart })
        .associate {
          val label = it.first().label
          label to it.buildFunctionCFG(label)
        }
      return ControlFlowGraph(funCFGs)
    }
  }
}
