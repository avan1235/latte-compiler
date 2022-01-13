package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.quadruple.BasicBlock.Companion.toBasicBlock
import ml.dev.kotlin.latte.quadruple.FunctionCFG.Companion.buildFunctionCFG
import ml.dev.kotlin.latte.util.splitAt


class CFG private constructor(
  val functions: Map<Label, FunctionCFG>,
) {
  fun orderedBlocks(): List<BasicBlock> = functions.values.flatMap { it.orderedBlocks() }
  fun removeNotReachableBlocks(): Unit = forEachStart { removeNotReachableBlocks() }
  fun transformToSSA(): Unit = forEachStart { transformToSSA() }
  fun transformFromSSA(): Unit = forEachStart { transformFromSSA() }

  private inline fun forEachStart(action: FunctionCFG.() -> Unit): Unit = functions.values.forEach(action)

  companion object {
    fun Iterable<Quadruple>.buildCFG(labelGenerator: () -> Label): CFG {
      val funCFGs = splitAt(first = { it is Labeled }, last = { it is Jumping })
        .map { it.toBasicBlock { CodeLabelQ(labelGenerator()) } }.asIterable()
        .splitAt(first = { it.isStart })
        .associate {
          val label = it.first().label
          label to it.buildFunctionCFG(label)
        }
      return CFG(funCFGs)
    }
  }
}
