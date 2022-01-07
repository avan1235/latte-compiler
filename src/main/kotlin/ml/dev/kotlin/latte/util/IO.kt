package ml.dev.kotlin.latte.util

import kotlin.system.exitProcess

@Suppress("NOTHING_TO_INLINE")
inline fun eprintln(message: Any?) = System.err.println(message)

data class FileLocation(val line: Int, val charPositionInLine: Int) {
  override fun toString() = "[$line:$charPositionInLine]"
}

data class Span(val from: FileLocation, val to: FileLocation)

data class LocalizedMessage(val description: String, val location: FileLocation? = null) {
  override fun toString(): String =
    "${location?.let { "$it " } ?: ""}${description.replaceFirstChar { it.lowercase() }}"
}

inline val String.msg: LocalizedMessage get() = LocalizedMessage(this)

fun exit(vararg lines: Any, exitCode: Int = 0) {
  lines.forEach { eprintln(it) }
  exitProcess(exitCode)
}
