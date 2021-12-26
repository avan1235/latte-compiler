package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.syntax.LatteLexer
import ml.dev.kotlin.syntax.LatteParser
import org.antlr.v4.runtime.*
import java.io.InputStream
import java.util.concurrent.CancellationException

fun InputStream.parse(): Program {
  val streams = CharStreams.fromStream(this)
  val lexer = LatteLexer(streams).reportErrorsAsExceptions()
  val tokenStream = CommonTokenStream(lexer)
  val parser = LatteParser(tokenStream).reportErrorsAsExceptions()
  val program = parser.program()
  return AstVisitor.visitProgram(program)
}

class ParseCancellationException(msg: String, val line: Int, val charPositionInLine: Int) : CancellationException(msg)

private fun <T : Recognizer<*, *>> T.reportErrorsAsExceptions() = apply {
  removeErrorListeners()
  addErrorListener(object : BaseErrorListener() {
    override fun syntaxError(
      recognizer: Recognizer<*, *>,
      offendingSymbol: Any,
      line: Int, charPositionInLine: Int,
      msg: String, e: RecognitionException
    ) = throw ParseCancellationException(msg, line, charPositionInLine)
  })
}
