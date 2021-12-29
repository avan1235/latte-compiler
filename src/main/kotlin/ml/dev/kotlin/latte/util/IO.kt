package ml.dev.kotlin.latte.util

@Suppress("NOTHING_TO_INLINE")
inline fun eprintln(message: Any?) = System.err.println(message)

data class FileLocation(val line: Int, val charPositionInLine: Int) {
  override fun toString() = "[$line:$charPositionInLine]"
}

data class Span(val from: FileLocation, val to: FileLocation)

data class ExceptionLocalizedMessage(val description: String, val location: FileLocation? = null) {
  override fun toString() = "${location?.let { "$it " } ?: ""}${description.lowercase()}"
}

inline val String.msg: ExceptionLocalizedMessage get() = ExceptionLocalizedMessage(this)
