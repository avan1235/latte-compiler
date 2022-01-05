package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*
import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg

interface Named {
  val name: String
}

data class Label(override val name: String) : Named

sealed interface ValueHolder {
  val type: Type
}

sealed class ConstValue(override val type: Type) : ValueHolder
data class IntConstValue(val int: Int) : ConstValue(IntType)
data class BooleanConstValue(val bool: Boolean) : ConstValue(BooleanType)
data class StringConstValue(val label: Label, val str: String) : ConstValue(StringType)

sealed interface VirtualReg : ValueHolder {
  val id: String
  val original: VirtualReg?
}

data class LocalValue(
  override val id: String,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg

data class ArgValue(
  override val id: String,
  val offset: Int,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg

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

fun VirtualReg.renameUsage(currIndex: CurrIndex): VirtualReg =
  if (original != null) err<VirtualReg>("Already renamed $this")
  else {
    val name = "$id#${currIndex(this) ?: err<String>("Cannot rename with null index: $this")}"
    when (this) {
      is ArgValue -> copy(id = name, original = this)
      is LocalValue -> copy(id = name, original = this)
    }
  }

fun VirtualReg.renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): VirtualReg {
  updateIndex(this)
  return renameUsage(currIndex)
}

data class BinOpQ(val to: VirtualReg, val left: VirtualReg, val op: NumOp, val right: ValueHolder) :
  Quadruple {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): BinOpQ {
    val right = if (right is VirtualReg) right.renameUsage(currIndex) else right
    val left = left.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return BinOpQ(to, left, op, right)
  }
}

data class UnOpQ(val to: VirtualReg, val op: UnOp, val from: VirtualReg) : Quadruple {
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
    val from = if (from is VirtualReg) from.renameUsage(currIndex) else from
    val to = to.renameDefinition(currIndex, updateIndex)
    return AssignQ(to, from)
  }
}

data class FunCallQ(val to: VirtualReg, val label: Label, val args: List<ValueHolder>) : Quadruple {
  val argsSize: Int = args.sumOf { it.type.size }
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCallQ {
    val args = args.map { if (it is VirtualReg) it.renameUsage(currIndex) else it }
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

data class CondJumpQ(val cond: VirtualReg, override val toLabel: Label) : Quadruple, Jumping {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): CondJumpQ {
    val cond = cond.renameUsage(currIndex)
    return CondJumpQ(cond, toLabel)
  }
}

data class RelCondJumpQ(val left: VirtualReg, val op: RelOp, val right: ValueHolder, override val toLabel: Label) :
  Quadruple, Jumping {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RelCondJumpQ {
    val right = if (right is VirtualReg) right.renameUsage(currIndex) else right
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
    val value = value?.let { if (it is VirtualReg) it.renameUsage(currIndex) else it }
    return RetQ(value)
  }
}

data class PhonyQ(private var _to: VirtualReg, private val _from: HashMap<Label, ValueHolder> = HashMap()) : Quadruple {
  private val original: VirtualReg = _to
  val to: VirtualReg get() = _to
  val from: Map<Label, ValueHolder> get() = _from
  fun renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): Unit =
    if (_to == original) _to = _to.renameDefinition(currIndex, updateIndex)
    else err<Unit>("Cannot rename Phony multiple times")

  fun renamePathUsage(from: Label, currIndex: CurrIndex) {
    _from[from] = original.renameUsage(currIndex)
  }

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple =
    err<Quadruple>("Cannot rename PhonyQ in single step - rename usages and definitions separately")
}

private fun <T> err(message: String): Nothing = throw IRException(message.msg)
