package ml.dev.kotlin.latte.util

/**
 * Based on "A Simple, Fast Dominance Algorithm" by Keith D. Cooper, Timothy J. Harvey, and Ken Kennedy
 */
class Dominance<V>(private val root: V, private val graph: Graph<V>) {

  private val postOrder: List<V> = ArrayList<V>(graph.size).also { postOrder ->
    val visited = HashSet<V>()
    fun go(v: V) {
      if (v in visited) return
      visited += v
      graph.successors(v).forEach { go(it) }
      postOrder += v
    }
    go(root)
  }

  private val postOderIdx: Map<V, Int> = postOrder.mapIndexed { idx, v -> v to idx }.toMap()
  private val reversePostOrder: List<V> = postOrder.reversed()
  private val _doms: MutableMap<V, V> = HashMap()
  private val _dfSet: MutableDefaultMap<V, HashSet<V>> = MutableDefaultMap({ HashSet() })

  fun dominator(v: V): V = _doms[v] ?: err("Dominator not calculated for $v in $graph")
  fun dominanceFrontiers(v: V): Set<V> = _dfSet[v]

  private fun calcDoms() {
    _doms[root] = root
    var changed = true
    while (changed) {
      changed = false
      for (b in reversePostOrder) {
        if (b == root) continue
        val predecessors = graph.predecessors(b)
        val firstPredecessor = predecessors.first { _doms[it] != null }
        var idom = firstPredecessor
        for (pred in predecessors) {
          if (pred == firstPredecessor) continue
          if (_doms[pred] != null) idom = intersect(pred, idom)
        }
        if (_doms[b] != idom) {
          _doms[b] = idom
          changed = true
        }
      }
    }
  }

  private fun intersect(b1: V, b2: V): V {
    var finger1 = b1
    var finger2 = b2
    while (finger1 != finger2) {
      while (postOderIdx[finger1]!! < postOderIdx[finger2]!!) finger1 = _doms[finger1]!!
      while (postOderIdx[finger2]!! < postOderIdx[finger1]!!) finger2 = _doms[finger2]!!
    }
    return finger1
  }

  private fun calcDFSet() {
    for (b in postOrder) {
      val predecessors = graph.predecessors(b)
      if (predecessors.size < 2) continue
      for (p in predecessors) {
        var runner = p
        while (runner !== _doms[b]) {
          _dfSet[runner] += b
          runner = _doms[runner]!!
        }
      }
    }
  }

  init {
    calcDoms()
    calcDFSet()
  }
}

private fun err(message: String): Nothing = throw GraphException(message.msg)

