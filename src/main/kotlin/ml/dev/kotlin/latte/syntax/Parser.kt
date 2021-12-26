package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.syntax.LatteLexer
import ml.dev.kotlin.syntax.LatteParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream

fun InputStream.parse(): Program {
    val streams = CharStreams.fromStream(this)
    val latteLexer = LatteLexer(streams)
    val tokenStream = CommonTokenStream(latteLexer)
    val program = LatteParser(tokenStream).program()
    return AstVisitor.visitProgram(program)
}
