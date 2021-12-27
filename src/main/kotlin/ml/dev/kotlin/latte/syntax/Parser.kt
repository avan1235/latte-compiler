package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.ExceptionLocalizedMessage
import ml.dev.kotlin.latte.util.FileLocation
import ml.dev.kotlin.latte.util.ParseException
import ml.dev.kotlin.syntax.LatteLexer
import ml.dev.kotlin.syntax.LatteParser
import org.antlr.v4.runtime.*
import java.io.InputStream

fun InputStream.parse(): Program {
  val streams = CharStreams.fromStream(this)
  val lexer = LatteLexer(streams).reportErrorsAsExceptions()
  val tokenStream = CommonTokenStream(lexer)
  val parser = LatteParser(tokenStream).reportErrorsAsExceptions()
  val program = parser.program()
  return AstVisitor.visitProgram(program)
}

private fun <T : Recognizer<*, *>> T.reportErrorsAsExceptions() = apply {
  removeErrorListeners()
  addErrorListener(object : BaseErrorListener() {
    override fun syntaxError(
      r: Recognizer<*, *>?, symbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?
    ) = throw ParseException(ExceptionLocalizedMessage(msg, FileLocation(line, charPositionInLine)))
  })
}
