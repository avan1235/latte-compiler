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
  if (isEmpty()) return setOf(emptyList())
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

inline fun <T> List<T>.forEachPairIndexed(f: (currIdx: Int, prev: T, curr: T) -> Unit) {
  for (idx in 1 until size) f(idx, this[idx - 1], this[idx])
}

fun <T> List<Set<T>>.intersect(): HashSet<T> = when (size) {
  0 -> HashSet()
  1 -> HashSet(first())
  else -> sortedBy { it.size }.fold(first().toHashSet()) { acc, set -> acc.apply { retainAll(set) } }
}

fun <T> Iterable<T>.nlString(transform: ((T) -> CharSequence)? = null) =
  joinToString("\n", "\n", "\n", transform = transform)

fun <T : Any> hashSetOfNotNull(element: T?): HashSet<T> = if (element != null) hashSetOf(element) else hashSetOf()

inline fun <U> get(count: Int, produce: (Int) -> U): List<U> = List(count, produce)
