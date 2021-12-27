package ml.dev.kotlin.latte.util

inline fun <T> Iterable<T>.splitAt(crossinline separator: (T) -> Boolean): List<List<T>> = sequence {
  var curr = mutableListOf<T>()
  for (elem in this@splitAt) {
    if (separator(elem)) {
      yield(curr)
      curr = mutableListOf(elem)
    } else curr += elem
  }
  yield(curr)
}.filter { it.isNotEmpty() }.toList()
