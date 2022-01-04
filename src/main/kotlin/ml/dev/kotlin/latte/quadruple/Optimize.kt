package ml.dev.kotlin.latte.quadruple

import java.util.*

fun <T> Sequence<T>.peepHoleOptimize(
  jumpToNext: Boolean = true,
  noJumpsToLabel: Boolean = true,
  extract: (T) -> Quadruple,
): Sequence<T> = run { if (jumpToNext) optimizeJumpToNext(extract) else this }
  .run { if (noJumpsToLabel) optimizeNoJumps(extract) else this }

private fun <T> Sequence<T>.optimizeNoJumps(extract: (T) -> Quadruple): Sequence<T> {
  val usedLabels = flatMap {
    when (val q = extract(it)) {
      is PhonyQ -> q.from.keys.asSequence()
      is Jumping -> sequenceOf(q.toLabel)
      else -> emptySequence()
    }
  }.filterNotNullTo(HashSet())
  return filter {
    val q = extract(it)
    q !is CodeLabelQ || q.label in usedLabels
  }
}

private fun <T> Sequence<T>.optimizeJumpToNext(extract: (T) -> Quadruple): Sequence<T> {
  tailrec fun TreeMap<Int, T>.go(): Sequence<T> {
    if (isEmpty()) return emptySequence()

    var next = firstKey()
    var removed = 0
    while (true) {
      val last = next
      next = higherKey(next) ?: break
      val jump = (this[last]?.let(extract) as? Jumping) ?: continue
      val codeLabel = (this[next]?.let(extract) as? Labeled) ?: continue
      if (jump.toLabel != codeLabel.label) continue
      this -= last
      removed += 1
    }
    return if (removed == 0) values.asSequence() else go()
  }
  return TreeMap<Int, T>().also { map ->
    forEachIndexed { idx, q -> map[idx] = q }
  }.go()
}
