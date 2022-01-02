package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg
import ml.dev.kotlin.latte.util.nlString

data class Phony(private var _to: VirtualReg, private val _from: HashMap<Label, ValueHolder> = HashMap()) : Quadruple {
  private val original: VirtualReg = _to
  val to: VirtualReg get() = _to
  val from: Map<Label, ValueHolder> get() = _from
  fun renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): Unit =
    if (_to == original) _to = _to.renameDefinition(currIndex, updateIndex)
    else throw IllegalStateException("Cannot rename Phony multiple times")

  fun renamePathUsage(from: Label, currIndex: CurrIndex): Unit =
    if (currIndex(original) != null) _from[from] = original.renameUsage(currIndex)
    else Unit
}

data class BasicBlock(
  private var _statements: MutableList<Quadruple>,
  val isStart: Boolean,
  val label: Label,
  val jumpQ: Jumping?,
  private var _phony: LinkedHashSet<Phony> = LinkedHashSet(),
) {
  val statements: List<Quadruple> get() = _statements
  val phony: Set<Phony> get() = _phony

  var linPred: BasicBlock? = null
    set(value) {
      if (value != this) field = value
    }
  var linSucc: BasicBlock? = null
    set(value) {
      if (value != this) field = value
    }

  fun mapStatements(f: (Quadruple) -> Quadruple) {
    _statements = _statements.mapTo(mutableListOf(), f)
  }

  fun filterPhony(f: (Phony) -> Boolean) {
    _phony = _phony.filterTo(LinkedHashSet(), f)
  }

  operator fun plusAssign(phony: Phony) {
    _phony += phony
  }

  operator fun plusAssign(statement: Quadruple) {
    _statements += statement
  }
}

fun Iterable<Quadruple>.toBasicBlock(labelGenerator: () -> CodeLabelQ): BasicBlock = toMutableList().run {
  val first = (firstOrNull() as? Labeled) ?: labelGenerator().also { add(index = 0, it) }
  val jumpingIdx = indexOfFirst { it is Jumping }
  if (jumpingIdx != -1 && jumpingIdx != size - 1) err("Basic block contains invalid jumps: ${nlString()}")
  if (count { it is Labeled } != 1) err("Basic block contains invalid labels: ${nlString()}")
  val jumping = lastOrNull() as? Jumping
  BasicBlock(this, first is FunCodeLabelQ, first.label, jumping)
}

private fun err(message: String): Nothing = throw IRException(message.msg)
