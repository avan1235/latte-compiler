package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.syntax.*

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
  val name: String
  val idx: Int
  val original: VirtualReg?
}

data class LocalValue(
  override val name: String,
  override val idx: Int,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg

data class ArgValue(
  override val name: String,
  override val idx: Int,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg

data class TempValue(
  override val name: String,
  override val idx: Int,
  override val type: Type,
  override val original: VirtualReg? = null,
) : VirtualReg

sealed interface Quadruple
sealed interface Jumping {
  val toLabel: Label?
}

sealed interface Labeled {
  val label: Label
}

typealias CurrIndex = (VirtualReg) -> Int?
typealias UpdateIndex = (VirtualReg) -> Unit

sealed interface Rename {
  fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple
}

fun VirtualReg.renameUsage(currIndex: CurrIndex): VirtualReg =
  if (original != null) throw IllegalStateException("Already renamed $this")
  else {
    val idx = currIndex(this) ?: throw IllegalStateException("Cannot rename with null index: $this")
    val name = "$name#$idx"
    when (this) {
      is ArgValue -> copy(name = name, original = this)
      is LocalValue -> copy(name = name, original = this)
      is TempValue -> copy(name = name, original = this)
    }
  }

fun VirtualReg.renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): VirtualReg {
  updateIndex(this)
  return renameUsage(currIndex)
}

data class BinOpQ(val to: VirtualReg, val left: VirtualReg, val op: NumOp, val right: ValueHolder) :
  Quadruple, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): BinOpQ {
    val right = if (right is VirtualReg) right.renameUsage(currIndex) else right
    val left = left.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return BinOpQ(to, left, op, right)
  }
}

data class UnOpQ(val to: VirtualReg, val op: UnOp, val from: VirtualReg) : Quadruple, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpQ(to, op, from)
  }
}

data class UnOpModQ(val to: VirtualReg, val op: UnOpMod, val from: VirtualReg) : Quadruple, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpModQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpModQ(to, op, from)
  }
}

data class AssignQ(val to: VirtualReg, val from: ValueHolder) : Quadruple, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): AssignQ {
    val from = if (from is VirtualReg) from.renameUsage(currIndex) else from
    val to = to.renameDefinition(currIndex, updateIndex)
    return AssignQ(to, from)
  }
}

data class FunCallQ(val to: VirtualReg, val label: Label, val args: List<ValueHolder>) : Quadruple, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCallQ {
    val args = args.map { if (it is VirtualReg) it.renameUsage(currIndex) else it }
    val to = to.renameDefinition(currIndex, updateIndex)
    return FunCallQ(to, label, args)
  }
}

data class FunCodeLabelQ(override val label: Label, val args: List<ArgValue>) : Quadruple, Labeled, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCodeLabelQ {
    val args = args.map { it.renameUsage(currIndex) as ArgValue }
    return FunCodeLabelQ(label, args)
  }
}

data class CodeLabelQ(override val label: Label) : Quadruple, Labeled

data class CondJumpQ(val cond: VirtualReg, override val toLabel: Label) : Quadruple, Jumping, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): CondJumpQ {
    val cond = cond.renameUsage(currIndex)
    return CondJumpQ(cond, toLabel)
  }
}

data class RelCondJumpQ(val left: VirtualReg, val op: RelOp, val right: ValueHolder, override val toLabel: Label) :
  Quadruple, Jumping, Rename {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RelCondJumpQ {
    val right = if (right is VirtualReg) right.renameUsage(currIndex) else right
    val left = left.renameUsage(currIndex)
    return RelCondJumpQ(left, op, right, toLabel)
  }
}

data class JumpQ(override val toLabel: Label) : Quadruple, Jumping
data class RetQ(val value: ValueHolder? = null) : Quadruple, Jumping, Rename {
  override val toLabel: Label? = null
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RetQ {
    val value = value?.let { if (it is VirtualReg) it.renameUsage(currIndex) else it }
    return RetQ(value)
  }
}

fun Quadruple.definedVar(): VirtualReg? = when (this) {
  is AssignQ -> to
  is BinOpQ -> to
  is UnOpQ -> to
  is UnOpModQ -> to
  is FunCallQ -> to
  is Phony -> to
  is RelCondJumpQ -> null
  is CondJumpQ -> null
  is RetQ -> null
  is JumpQ -> null
  is CodeLabelQ -> null
  is FunCodeLabelQ -> null
}

fun Quadruple.usedVar(): Sequence<VirtualReg> = when (this) {
  is AssignQ -> sequenceOf(from as? VirtualReg)
  is BinOpQ -> sequenceOf(left, right as? VirtualReg)
  is UnOpQ -> sequenceOf(from)
  is UnOpModQ -> sequenceOf(from)
  is FunCallQ -> args.asSequence().filterIsInstance<VirtualReg>()
  is Phony -> from.values.asSequence().filterIsInstance<VirtualReg>()
  is RelCondJumpQ -> sequenceOf(left, right as? VirtualReg)
  is CondJumpQ -> sequenceOf(cond)
  is RetQ -> sequenceOf(value as? VirtualReg)
  is FunCodeLabelQ -> args.asSequence()
  is CodeLabelQ -> emptySequence()
  is JumpQ -> emptySequence()
}.filterNotNull()
