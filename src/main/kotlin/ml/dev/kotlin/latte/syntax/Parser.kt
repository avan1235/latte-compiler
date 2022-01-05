package ml.dev.kotlin.latte.syntax

import ml.dev.kotlin.latte.util.FileLocation
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.ParseException
import org.antlr.v4.runtime.*
import java.io.InputStream
import java.nio.charset.Charset

fun InputStream.parse(charset: Charset = Charsets.UTF_8): Program {
  val streams = CharStreams.fromStream(this, charset)
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
    ) = throw ParseException(LocalizedMessage(msg, FileLocation(line, charPositionInLine)))
  })
}
