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

fun <T> List<List<T>>.combinations(): Set<List<T>> {
  if (isEmpty()) return emptySet()
  var combinations = HashSet<List<T>>()
  for (i in this[0]) combinations += ArrayList<T>().also { it += i }

  repeat(size - 1) { idx ->
    val currList = this[idx + 1]
    val combinationsUpdate = HashSet<List<T>>()
    for (first in combinations) {
      for (currElem in currList) {
        val newList = ArrayList(first)
        newList += currElem
        combinationsUpdate += newList
      }
    }
    combinations = combinationsUpdate
  }
  return combinations
}


fun <T> Iterable<T>.nlString(transform: ((T) -> CharSequence)? = null) =
  joinToString("\n", "\n", "\n", transform = transform)

interface DirectedGraph<V> {
  val nodes: Set<V>
  fun successors(v: V): Set<V>
  fun predecessors(v: V): Set<V>

  fun reachable(from: V): Set<V> {
    val visited = LinkedHashSet<V>()
    val queue = ArrayDeque<V>()
    tailrec fun go(from: V) {
      visited += from
      successors(from).forEach { if (it !in visited) queue += it }
      go(queue.removeLastOrNull() ?: return)
    }
    return visited.also { go(from) }
  }

  fun topologicalSort(): TopologicalSortResult<V> {
    val succ = MutableDefaultMap<V, Set<V>>({ successors(it) })
    val inDegree = MutableDefaultMap<V, Int>({ 0 })
    for (n in nodes) succ[n].forEach { inDegree[it] += 1 }

    val queue = ArrayDeque<V>()
    nodes.forEach { if (inDegree[it] == 0) queue += it }

    val visited = HashSet<V>()
    val topOrder = ArrayList<V>()
    while (true) {
      val u = queue.removeFirstOrNull()?.also { topOrder += it } ?: break
      succ[u].forEach {
        inDegree[it] -= 1
        if (inDegree[it] == 0) queue += it
      }
      visited += u
    }
    if (visited != nodes) return WithCycle(nodes - visited)
    return Sorted(topOrder)
  }
}

abstract class UndirectedGraph<V> : DirectedGraph<V> {
  final override fun successors(v: V): Set<V> = connected(v)
  final override fun predecessors(v: V): Set<V> = connected(v)

  abstract fun connected(v: V): Set<V>
}

inline fun <U> get(count: Int, produce: (Int) -> U): List<U> = List(count, produce)

sealed interface TopologicalSortResult<V>
data class Sorted<V>(val sorted: List<V>) : TopologicalSortResult<V>
data class WithCycle<V>(val cycle: Set<V>) : TopologicalSortResult<V>
