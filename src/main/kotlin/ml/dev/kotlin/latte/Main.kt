package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.quadruple.toIR
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.LatteException
import ml.dev.kotlin.latte.util.eprintln
import java.io.File

fun main(args: Array<String>): Unit = args.forEach { path ->
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
}

private fun File.runCompiler() = takeIf { it.isFile }?.inputStream()
  ?.parse()?.also { println("AST:\n$it") }
  ?.typeCheck()
  ?.toIR()?.also { println("IR:\n$it") }
