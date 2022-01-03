package ml.dev.kotlin.latte.quadruple

import java.util.*

fun Iterable<Quadruple>.peepHoleOptimize(
  jumpToNext: Boolean = true,
  noJumpsToLabel: Boolean = true,
): List<Quadruple> = run { if (jumpToNext) optimizeJumpToNext() else this }
  .run { if (noJumpsToLabel) optimizeNoJumps() else this }
  .toList()

private fun Iterable<Quadruple>.optimizeNoJumps(): List<Quadruple> {
  val usedLabels = asSequence().flatMap {
    when (it) {
      is PhonyQ -> it.from.keys.asSequence()
      is Jumping -> sequenceOf(it.toLabel)
      else -> emptySequence()
    }
  }.filterNotNullTo(HashSet())
  return filter { it !is CodeLabelQ || it.label in usedLabels }
}

private fun Iterable<Quadruple>.optimizeJumpToNext(): List<Quadruple> {
  tailrec fun TreeMap<Int, Quadruple>.go(): List<Quadruple> {
    if (isEmpty()) return emptyList()

    var next = firstKey()
    var removed = 0
    while (true) {
      val last = next
      next = higherKey(next) ?: break
      val jump = (this[last] as? Jumping) ?: continue
      val codeLabel = (this[next] as? Labeled) ?: continue
      if (jump.toLabel != codeLabel.label) continue
      this -= last
      removed += 1
    }
    return if (removed == 0) values.toList() else go()
  }
  return TreeMap<Int, Quadruple>().also { map ->
    forEachIndexed { idx, q -> map[idx] = q }
  }.go()
}
