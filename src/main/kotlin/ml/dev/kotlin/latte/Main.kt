package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.asm.AllocatorStrategy
import ml.dev.kotlin.latte.asm.AllocatorStrategyProducer
import ml.dev.kotlin.latte.asm.compile
import ml.dev.kotlin.latte.asm.nasm
import ml.dev.kotlin.latte.quadruple.toIR
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.LatteException
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.exit
import java.io.File

fun main(args: Array<String>): Unit = args.takeIf { it.isNotEmpty() }?.forEach { path ->
  try {
    val inputFile = File(path)
    val asmCode = inputFile.runCompiler()
    val asmFile = inputFile.dir.resolve("${inputFile.nameWithoutExtension}.s")
    asmFile.writeText(asmCode)
    nasm(asmFile).run { oFile.delete() }
    exit("OK", exitCode = 0)
  } catch (e: LatteException) {
    exit("ERROR", e.userMessage, exitCode = 2)
  } catch (e: Throwable) {
    exit("ERROR", e, exitCode = 3)
  }
} ?: exit("Usage: ./latte <input-file-paths>", exitCode = 1)

internal val DEFAULT_ALLOCATOR_STRATEGY: AllocatorStrategyProducer =
  { analysis, manager -> AllocatorStrategy(analysis, manager) }

internal fun File.runCompiler(
  strategy: AllocatorStrategyProducer = DEFAULT_ALLOCATOR_STRATEGY
): String = inputStream()
  .parse()
  .typeCheck()
  .toIR().apply {
    graph.removeNotReachableBlocks()
    graph.transformToSSA()
    // TODO run optimizations on graph in SSA
    graph.transformFromSSA()
  }
  .compile(strategy)
