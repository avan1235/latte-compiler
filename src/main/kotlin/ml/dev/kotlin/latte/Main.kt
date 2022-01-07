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
import ml.dev.kotlin.latte.util.eprintln
import ml.dev.kotlin.latte.util.unit
import java.io.File

fun main(args: Array<String>): Unit = args.takeIf { it.isNotEmpty() }?.forEach { path ->
  try {
    val inputFile = File(path)
    val asmCode = inputFile.runCompiler()
    val asmFile = inputFile.dir.resolve("${inputFile.nameWithoutExtension}.s")
    with(asmFile) {
      writeText(asmCode)
      nasm(this).run { oFile.delete() }.unit()
    }
    println("OK")
  } catch (e: LatteException) {
    eprintln(e.userMessage)
    eprintln("ERROR")
  } catch (e: Throwable) {
    eprintln(e.message)
    eprintln("ERROR")
  }
} ?: println("Usage: ./latte <input-file-paths>")

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
