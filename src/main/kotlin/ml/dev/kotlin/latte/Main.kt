package ml.dev.kotlin.latte

import ml.dev.kotlin.latte.quadruple.toIR
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.typeCheck
import java.io.File

fun main(args: Array<String>): Unit = args.forEach { path ->
  File(path).takeIf { it.isFile }?.inputStream()
    ?.parse()?.also { println("AST:\n$it") }
    ?.typeCheck()
    ?.toIR()?.also { println("IR:\n$it") }
}
