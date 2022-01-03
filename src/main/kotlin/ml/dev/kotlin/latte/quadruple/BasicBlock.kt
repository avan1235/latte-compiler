package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString
import java.util.*

class BasicBlock private constructor(
  val isStart: Boolean,
  val label: Label,
  val jumpQ: Jumping?,
  private var _statements: LinkedList<Quadruple>,
  private var _phony: LinkedHashSet<PhonyQ> = LinkedHashSet(),
) {
  val rawStatements: Iterable<Quadruple> get() = _statements
  val statements: Sequence<Quadruple>
    get() = if (_phony.isEmpty()) _statements.asSequence() else sequence {
      yield(_statements.first)
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

  fun mapStatements(f: (Int, Quadruple) -> Quadruple?) {
    _statements = _statements.mapIndexedNotNullTo(LinkedList(), f)
  }

  fun cleanPhony() {
    _phony = LinkedHashSet()
  }

  operator fun plusAssign(phony: PhonyQ) {
    _phony += phony
  }

  operator fun plusAssign(statement: Quadruple): Unit =
    if (_statements.lastOrNull() is Jumping) {
      val jump = _statements.removeLast()
      _statements += statement
      _statements += jump
    } else _statements += statement

  companion object {
    fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
      val first = (firstOrNull() as? Labeled) ?: labelGenerator().also { add(index = 0, it) }
      val jumpingIdx = indexOfFirst { it is Jumping }
      if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
      if (count { it is Labeled } != 1) err("Basic block contains invalid labels: ${nlString()}")
      val jumping = lastOrNull() as? Jumping
      BasicBlock(first is FunCodeLabelQ, first.label, jumping, LinkedList(this))
    }
  }
}

private fun err(message: String): Nothing = throw IRException(message.msg)
