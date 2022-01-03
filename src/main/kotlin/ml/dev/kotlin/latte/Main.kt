package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.asm.compile
import ml.dev.kotlin.latte.quadruple.*
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.LatteException
import ml.dev.kotlin.latte.util.eprintln
import ml.dev.kotlin.latte.util.nlString
import java.io.File

fun main(args: Array<String>): Unit = args.takeIf { it.isNotEmpty() }?.forEach { path ->
  try {
    File(path).runCompiler()
    println("OK")
  } catch (e: LatteException) {
    eprintln(e.userMessage)
    eprintln("ERROR")
  } catch (e: Throwable) {
    eprintln("Unknown error: $e")
    eprintln("ERROR")
  }
} ?: println("Usage: ./latte <input-file-paths>")

internal fun File.runCompiler(): String = inputStream()
  .parse()
  .typeCheck()
  .toIR().also { it.graph.transformFromSSA() }
  .compile()
