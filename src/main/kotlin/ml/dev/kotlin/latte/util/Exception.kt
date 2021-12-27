package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

data class FileLocation(val line: Int, val charPositionInLine: Int) {
  override fun toString() = "[$line:$charPositionInLine]"
}

data class ExceptionLocalizedMessage(val description: String, val location: FileLocation? = null) {
  override fun toString() = "$location $description"
}

abstract class LatteException(val userMessage: ExceptionLocalizedMessage) : CancellationException("$userMessage")

sealed class FrontendException(userMessage: ExceptionLocalizedMessage) : LatteException(userMessage)
class ParseException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)
class TypeCheckException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)
