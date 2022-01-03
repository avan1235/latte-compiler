package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString

class BasicBlock private constructor(
  val isStart: Boolean,
  val label: Label,
  val jumpQ: Jumping?,
  private var _statements: ArrayList<Quadruple>,
  private var _phony: LinkedHashSet<PhonyQ> = LinkedHashSet(),
) {
  val rawStatements: List<Quadruple> get() = _statements
  val statements: Sequence<Quadruple>
    get() = if (_phony.isEmpty()) _statements.asSequence() else sequence {
      _statements.firstOrNull()?.let { yield(it) }
      yieldAll(_phony)
      _statements.forEachIndexed { idx, stmt -> if (idx > 0) yield(stmt) }
    }

  val phony: Set<PhonyQ> get() = _phony

  var linPred: BasicBlock? = null
    set(value) {
      if (value != this) field = value
    }

  var linSucc: BasicBlock? = null
    set(value) {
      if (value != this) field = value
    }

  fun mapStatements(f: (Int, Quadruple) -> Quadruple) {
    _statements = _statements.mapIndexedTo(arrayListOf(), f)
  }

  fun cleanPhony() {
    _phony = LinkedHashSet()
  }

  operator fun plusAssign(phony: PhonyQ) {
    _phony += phony
  }

  operator fun plusAssign(statement: Quadruple): Unit =
    if (_statements.lastOrNull() is Jumping) {
      _statements.add(_statements.size - 1, statement)
    } else _statements += statement

  companion object {
    fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
      val first = (firstOrNull() as? Labeled) ?: labelGenerator().also { add(index = 0, it) }
      val jumpingIdx = indexOfFirst { it is Jumping }
      if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
      if (count { it is Labeled } != 1) err("Basic block contains invalid labels: ${nlString()}")
      val jumping = lastOrNull() as? Jumping
      BasicBlock(first is FunCodeLabelQ, first.label, jumping, ArrayList(this))
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
