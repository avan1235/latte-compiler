package ml.dev.kotlin.latte.util

/**
 * Based on "A Simple, Fast Dominance Algorithm" by Keith D. Cooper, Timothy J. Harvey, and Ken Kennedy
 */
class Dominance<V>(private val root: V, private val graph: Graph<V>) {

  private val cachedPredecessors: DefaultMap<V, Set<V>> = MutableDefaultMap({ graph.predecessors(it) })
  private val cachedSuccessors: DefaultMap<V, Set<V>> = MutableDefaultMap({ graph.successors(it) })
  private val postOrder: List<V> = ArrayList<V>(graph.size).also { postOrder ->
    val visited = HashSet<V>()
    fun go(v: V) {
      if (v in visited) return
      visited += v
      cachedSuccessors[v].forEach { go(it) }
      postOrder += v
    }
    go(root)
  }
  private val postOderIdx: Map<V, Int> = postOrder.mapIndexed { idx, v -> v to idx }.toMap()
  private val reversePostOrder: List<V> = postOrder.reversed()
  private val _dominator: MutableMap<V, V> = HashMap()
  private val _frontiers: MutableDefaultMap<V, HashSet<V>> = MutableDefaultMap({ HashSet() })
  val dominanceTree: DominanceTree<V> by lazy { calcDominanceTree() }

  /**
   * For node v we define its dominator a node u such as u dominates v, so every path
   * from root node to v goes through u. It's called strict dominator if u != v.
   */
  fun dominator(v: V): V = _dominator[v] ?: err("Dominator not calculated for $v in $graph")

  /**
   * For node v we define its dominance frontiers as a set of nodes, which has
   * predecessors dominated by v, but they are not strictly dominated by v
   * (so there exists some other path from root node to these nodes that don't
   * go through v)
   */
  fun frontiers(v: V): Set<V> = _frontiers[v]

  private fun calcDominators() {
    _dominator[root] = root
    var changed = true
    while (changed) {
      changed = false
      for (b in reversePostOrder) {
        if (b == root) continue
        val predecessors = cachedPredecessors[b]
        val firstPredecessor = predecessors.first { _dominator[it] != null }
        var idom = firstPredecessor
        for (pred in predecessors) {
          if (pred == firstPredecessor) continue
          if (_dominator[pred] != null) idom = intersect(pred, idom)
        }
        if (_dominator[b] != idom) {
          _dominator[b] = idom
          changed = true
        }
      }
    }
  }

  private fun intersect(b1: V, b2: V): V {
    var finger1 = b1
    var finger2 = b2
    while (finger1 != finger2) {
      while (postOderIdx[finger1]!! < postOderIdx[finger2]!!) finger1 = _dominator[finger1]!!
      while (postOderIdx[finger2]!! < postOderIdx[finger1]!!) finger2 = _dominator[finger2]!!
    }
    return finger1
  }

  private fun calcDominanceFrontiers() {
    for (b in postOrder) {
      val predecessors = cachedPredecessors[b]
      if (predecessors.size < 2) continue
      for (p in predecessors) {
        var runner = p
        while (runner != _dominator[b]) {
          _frontiers[runner] += b
          runner = _dominator[runner]!!
        }
      }
    }
  }

  private fun calcDominanceTree(): DominanceTree<V> {
    val nodes = graph.nodes.toHashSet()
    val size = graph.size
    val successors = MutableDefaultMap<V, HashSet<V>>({ HashSet() })
    val predecessors = MutableDefaultMap<V, HashSet<V>>({ HashSet() })
    nodes.forEach {
      val dominator = dominator(it)
      if (it != root) predecessors[it] += dominator
      successors[dominator] += it
    }
    return DominanceTree(nodes, size, successors, predecessors)
  }

  init {
    calcDominators()
    calcDominanceFrontiers()
  }
}

private fun err(message: String): Nothing = throw GraphException(message.msg)

data class DominanceTree<V>(
  override val nodes: Set<V>,
  override val size: Int,
  private val successors: MutableDefaultMap<V, HashSet<V>>,
  private val predecessors: MutableDefaultMap<V, HashSet<V>>,
) : Graph<V> {
  override fun successors(v: V): Set<V> = successors[v]
  override fun predecessors(v: V): Set<V> = predecessors[v]
}

