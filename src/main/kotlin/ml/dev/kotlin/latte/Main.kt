package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.asm.AllocatorStrategy
import ml.dev.kotlin.latte.asm.AllocatorStrategyProducer
import ml.dev.kotlin.latte.asm.compile
import ml.dev.kotlin.latte.asm.nasm
import ml.dev.kotlin.latte.quadruple.optimize
import ml.dev.kotlin.latte.quadruple.printInstructions
import ml.dev.kotlin.latte.quadruple.toIR
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.LatteException
import ml.dev.kotlin.latte.util.exit
import ml.dev.kotlin.latte.util.withExtension
import java.io.File

fun main(args: Array<String>): Unit = args.takeIf { it.isNotEmpty() }?.forEach { path ->
  try {
    val inputFile = File(path)
    val asmCode = inputFile.runCompiler()
    val asmFile = inputFile.withExtension(".s")
    asmFile.writeText(asmCode)
    nasm(asmFile).run { oFile.delete() }
    exit("OK", exitCode = 0)
  } catch (e: LatteException) {
    exit("ERROR", e.userMessage, exitCode = 2)
  } catch (e: Throwable) {
    exit("ERROR", e, e.stackTrace, exitCode = 3)
  }
} ?: exit("Usage: ./latte <input-file-paths>", exitCode = 1)

internal val DEFAULT_ALLOCATOR_STRATEGY: AllocatorStrategyProducer =
  { analysis, manager -> AllocatorStrategy(analysis, manager) }

internal fun File.runCompiler(
  removeTempDefs: Boolean = true,
  propagateConstants: Boolean = true,
  simplifyExpr: Boolean = true,
  removeDeadAssignQ: Boolean = true,
  lcse: Boolean = true,
  gcse: Boolean = true,
  strategy: AllocatorStrategyProducer = DEFAULT_ALLOCATOR_STRATEGY,
  printIR: Boolean = false,
): String = inputStream()
  .parse()
  .typeCheck()
  .toIR().apply {
    graph.removeNotReachableBlocks()
    graph.transformToSSA()
    graph.optimize(removeTempDefs, propagateConstants, simplifyExpr, removeDeadAssignQ, lcse, gcse)
    graph.transformFromSSA()
    if (printIR) graph.printInstructions()
  }
  .compile(strategy)
