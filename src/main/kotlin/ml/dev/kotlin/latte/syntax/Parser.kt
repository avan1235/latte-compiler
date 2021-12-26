package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.syntax.LatteLexer
import ml.dev.kotlin.syntax.LatteParser
import org.antlr.v4.gui.Trees
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

fun main()  {
    val file = File("/media/shared/CLionProjects/latte-compiler/test/data/good/core013.lat")
    val streams = CharStreams.fromPath(file.toPath())
    val latteLexer = LatteLexer(streams)
    val tokenStream = CommonTokenStream(latteLexer)
    val parser = LatteParser(tokenStream)
    val program = parser.program()
    Trees.inspect(program, parser)
}