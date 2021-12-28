package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

data class FileLocation(val line: Int, val charPositionInLine: Int) {
  override fun toString() = "[$line:$charPositionInLine]"
}

data class Span(val from: FileLocation, val to: FileLocation)

data class ExceptionLocalizedMessage(val description: String, val location: FileLocation? = null) {
  override fun toString() = "$location ${description.lowercase()}"
}

inline val String.msg: ExceptionLocalizedMessage get() = ExceptionLocalizedMessage(this)

abstract class LatteException(val userMessage: ExceptionLocalizedMessage) : CancellationException("$userMessage")

sealed class FrontendException(userMessage: ExceptionLocalizedMessage) : LatteException(userMessage)
class ParseException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)
class TypeCheckException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)

class IRException(userMessage: ExceptionLocalizedMessage) : LatteException(userMessage)
