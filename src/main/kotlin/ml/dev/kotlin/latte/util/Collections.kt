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
  fun successors(v: V): Set<V>
  fun predecessors(v: V): Set<V>

  fun reachable(from: V): Set<V> {
    val visited = HashSet<V>()
    val queue = ArrayDeque<V>()
    tailrec fun go(from: V) {
      visited += from
      successors(from).forEach { if (it !in visited) queue += it }
      go(queue.removeLastOrNull() ?: return)
    }
    return visited.also { go(from) }
  }
}

inline fun <U> get(count: Int, produce: (Int) -> U): List<U> = List(count, produce)
