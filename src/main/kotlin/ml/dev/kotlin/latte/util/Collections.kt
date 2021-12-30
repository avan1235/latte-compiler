package ml.dev.kotlin.latte.util

inline fun <T> Iterable<T>.splitAt(
  crossinline first: (T) -> Boolean = { false },
  crossinline last: (T) -> Boolean = { false },
): Sequence<List<T>> = sequence {
  var curr = mutableListOf<T>()
  for (elem in this@splitAt) when {
    first(elem) -> {
      yield(curr)
      curr = mutableListOf(elem)
    }
    last(elem) -> {
      curr += elem
      yield(curr)
      curr = mutableListOf()
    }
    else -> curr += elem
  }
  yield(curr)
}.filter { it.isNotEmpty() }

fun <T> Iterable<T>.nlString(transform: ((T) -> CharSequence)? = null) =
  joinToString("\n", "\n", "\n", transform = transform)

interface Graph<V> {
  val nodes: Set<V>
  val size: Int
  fun successors(v: V): LinkedHashSet<V>
  fun predecessors(v: V): LinkedHashSet<V>
}
