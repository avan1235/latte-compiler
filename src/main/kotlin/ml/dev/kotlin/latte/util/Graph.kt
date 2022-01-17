package ml.dev.kotlin.latte.util


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

sealed interface TopologicalSortResult<V>
data class Sorted<V>(val sorted: List<V>) : TopologicalSortResult<V>
data class WithCycle<V>(val cycle: Set<V>) : TopologicalSortResult<V>
