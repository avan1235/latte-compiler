package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

data class FileLocation(val line: Int, val charPositionInLine: Int) {
  override fun toString() = "[$line:$charPositionInLine]"
}

data class ExceptionLocalizedMessage(val description: String, val location: FileLocation? = null) {
  override fun toString() = "$location $description"
}

interface WithMessage {
  val userMessage: ExceptionLocalizedMessage
}

class ParseCancellationException(override val userMessage: ExceptionLocalizedMessage) : CancellationException(), WithMessage
