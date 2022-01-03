package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions

internal class FlowAnalyzerTest {
}

//private fun testFlow(
//  program: String,
//) {
//  val (graph, str) = program.byteInputStream().parse().typeCheck().toIR()
//  with(graph) {
//    removeNotReachableBlocks()
//    transformToSSA()
//  }
//  val instructions = graph.instructions().run { if (optimize) peepHoleOptimize() else this }
//  val repr = instructions.nlString { it.repr() }
//  Assertions.assertEquals("\n${irRepresentation.trimIndent()}\n", repr)
//  Assertions.assertEquals(strings.toMap() + ("" to EMPTY_STRING_LABEL.name), str.mapValues { it.value.name })
//  assert(instructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
//}
