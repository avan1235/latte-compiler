package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.asm.compile
import ml.dev.kotlin.latte.asm.nasm
import ml.dev.kotlin.latte.quadruple.instructions
import ml.dev.kotlin.latte.quadruple.peepHoleOptimize
import ml.dev.kotlin.latte.quadruple.repr
import ml.dev.kotlin.latte.quadruple.toIR
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.*
import java.io.File

fun main(args: Array<String>): Unit = args.takeIf { it.isNotEmpty() }?.forEach { path ->
  try {
    val inputFile = File(path)
    inputFile.runCompiler().let { code ->
      val asm = inputFile.dir.resolve("${inputFile.nameWithoutExtension}.s").apply { writeText(code) }
      nasm(asm).run { oFile.delete() }.unit()
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

internal fun File.runCompiler(): String = inputStream()
  .parse()
  .typeCheck()
  .toIR().apply {
    graph.removeNotReachableBlocks()
    graph.transformToSSA()
    // TODO run optimizations on graph in SSA
    graph.transformFromSSA()
  }
  .compile()
