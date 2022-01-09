package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.syntax.PrimitiveType.*
import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg

interface Named {
  val name: String
}

data class Label(override val name: String) : Named

sealed interface ValueHolder {
  val type: Type
  fun renameUsage(currIndex: CurrIndex): ValueHolder = this
}

sealed class ConstValue(override val type: Type) : ValueHolder
data class IntConstValue(val int: Int) : ConstValue(IntType)
data class BooleanConstValue(val bool: Boolean) : ConstValue(BooleanType)
data class StringConstValue(val label: Label, val str: String) : ConstValue(StringType)
data class NullConstValue(val label: Label, val str: String) : ConstValue(NullType)

sealed class VirtualReg(open val id: String, open val original: VirtualReg?) : ValueHolder {
  abstract override fun renameUsage(currIndex: CurrIndex): VirtualReg
  protected fun createRenamedId(currIndex: CurrIndex): String = if (original == null) {
    "$id#${currIndex(this) ?: err<String>("Cannot rename with null index: $this")}"
  } else err("Already renamed $this")

  fun renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): VirtualReg {
    updateIndex(this)
    return renameUsage(currIndex)
  }
}

data class LocalValue(
  override val id: String,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg(id, original) {
  override fun renameUsage(currIndex: CurrIndex): LocalValue =
    LocalValue(createRenamedId(currIndex), type, this)
}

data class ArgValue(
  override val id: String,
  val offset: Int,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg(id, original) {
  override fun renameUsage(currIndex: CurrIndex): VirtualReg = when (currIndex(this)) {
    FIRST_DEFINITION_COUNT -> ArgValue(createRenamedId(currIndex), offset, type, this)
    else -> LocalValue(createRenamedId(currIndex), type, this)
  }
}

sealed interface Rename {
  fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple
}

sealed interface Quadruple : Rename

sealed interface Jumping {
  val toLabel: Label?
}

sealed interface Labeled {
  val label: Label
}

typealias CurrIndex = (VirtualReg) -> Int?
typealias UpdateIndex = (VirtualReg) -> Unit

data class BinOpQ(val to: VirtualReg, val left: ValueHolder, val op: NumOp, val right: ValueHolder) :
  Quadruple {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): BinOpQ {
    val right = right.renameUsage(currIndex)
    val left = left.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return BinOpQ(to, left, op, right)
  }
}

data class UnOpQ(val to: VirtualReg, val op: UnOp, val from: ValueHolder) : Quadruple {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpQ(to, op, from)
  }
}

data class UnOpModQ(val to: VirtualReg, val op: UnOpMod, val from: VirtualReg) : Quadruple {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpModQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpModQ(to, op, from)
  }
}

data class AssignQ(val to: VirtualReg, val from: ValueHolder) : Quadruple {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): AssignQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return AssignQ(to, from)
  }
}

data class FunCallQ(val to: VirtualReg, val label: Label, val args: List<ValueHolder>) : Quadruple {
  val argsSize: Int = args.sumOf { it.type.size }
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCallQ {
    val args = args.map { it.renameUsage(currIndex) }
    val to = to.renameDefinition(currIndex, updateIndex)
    return FunCallQ(to, label, args)
  }
}

data class FunCodeLabelQ(override val label: Label, val args: List<ArgValue>) : Quadruple, Labeled {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCodeLabelQ {
    val args = args.map { it.renameDefinition(currIndex, updateIndex) as ArgValue }
    return FunCodeLabelQ(label, args)
  }
}

data class CodeLabelQ(override val label: Label) : Quadruple, Labeled {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple = this
}

data class CondJumpQ(val cond: ValueHolder, override val toLabel: Label) : Quadruple, Jumping {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): CondJumpQ {
    val cond = cond.renameUsage(currIndex)
    return CondJumpQ(cond, toLabel)
  }
}

data class RelCondJumpQ(val left: ValueHolder, val op: RelOp, val right: ValueHolder, override val toLabel: Label) :
  Quadruple, Jumping {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RelCondJumpQ {
    val right = right.renameUsage(currIndex)
    val left = left.renameUsage(currIndex)
    return RelCondJumpQ(left, op, right, toLabel)
  }
}

data class JumpQ(override val toLabel: Label) : Quadruple, Jumping {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple = this
}

data class RetQ(val value: ValueHolder? = null) : Quadruple, Jumping {
  override val toLabel: Label? = null
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RetQ {
    val value = value?.renameUsage(currIndex)
    return RetQ(value)
  }
}

data class PhonyQ(private var _to: VirtualReg, private val _from: HashMap<Label, ValueHolder> = HashMap()) : Quadruple {
  private val original: VirtualReg = _to
  val to: VirtualReg get() = _to
  val from: Map<Label, ValueHolder> get() = _from
  fun renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): Unit =
    if (_to == original) _to = _to.renameDefinition(currIndex, updateIndex)
    else err("Cannot rename Phony multiple times")

  fun renamePathUsage(from: Label, currIndex: CurrIndex) {
    _from[from] = original.renameUsage(currIndex)
  }

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple =
    err("Cannot rename PhonyQ in single step - rename usages and definitions separately")
}

private fun <T> err(message: String): T = throw IRException(message.msg)
