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
data class LabelConstValue(val label: Label) : ConstValue(VoidRefType)
object NullConstValue : ConstValue(VoidRefType)

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

sealed interface Quadruple {
  fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple
  fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple
}

sealed interface Jumping {
  val toLabel: Label?
}

sealed interface Labeled {
  val label: Label
}

sealed interface DefiningVar {
  val to: VirtualReg
  fun redefine(asVar: VirtualReg): Quadruple
}

typealias CurrIndex = (VirtualReg) -> Int?
typealias UpdateIndex = (VirtualReg) -> Unit

data class BinOpQ(override val to: VirtualReg, val left: ValueHolder, val op: NumOp, val right: ValueHolder) :
  Quadruple, DefiningVar {
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(left = constants[left] ?: left, right = constants[right] ?: right)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): BinOpQ {
    val right = right.renameUsage(currIndex)
    val left = left.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return BinOpQ(to, left, op, right)
  }
}

data class UnOpQ(override val to: VirtualReg, val op: UnOp, val from: ValueHolder) : Quadruple, DefiningVar {
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(from = constants[from] ?: from)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpQ(to, op, from)
  }
}

data class UnOpModQ(override val to: VirtualReg, val op: UnOpMod, val from: VirtualReg) : Quadruple, DefiningVar {
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple = this
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): UnOpModQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return UnOpModQ(to, op, from)
  }
}

data class AssignQ(override val to: VirtualReg, val from: ValueHolder) : Quadruple, DefiningVar {
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(from = constants[from] ?: from)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): AssignQ {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return AssignQ(to, from)
  }
}

data class StoreQ(val at: ValueHolder, val offset: Bytes, val from: ValueHolder) : Quadruple {
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(at = constants[at] ?: at, from = constants[from] ?: from)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple {
    val from = from.renameUsage(currIndex)
    val to = at.renameUsage(currIndex)
    return StoreQ(to, offset, from)
  }
}

data class LoadQ(override val to: VirtualReg, val from: ValueHolder, val offset: Bytes) : Quadruple, DefiningVar {
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(from = constants[from] ?: from)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple {
    val from = from.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return LoadQ(to, from, offset)
  }
}

data class FunCallQ(
  override val to: VirtualReg,
  val label: Label,
  val args: List<ValueHolder>
) : Quadruple, DefiningVar {
  val argsSize: Int = args.sumOf { it.type.size }
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(args = args.map { constants[it] ?: it })

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCallQ {
    val args = args.map { it.renameUsage(currIndex) }
    val to = to.renameDefinition(currIndex, updateIndex)
    return FunCallQ(to, label, args)
  }
}

data class MethodCallQ(
  override val to: VirtualReg,
  val self: ValueHolder,
  val ident: String,
  val args: List<ValueHolder>,
  val argsTypes: List<Type>
) : Quadruple, DefiningVar {
  val argsSize: Int = args.sumOf { it.type.size } + self.type.size
  override fun redefine(asVar: VirtualReg): Quadruple = copy(to = asVar)
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(self = constants[self] ?: self, args = args.map { constants[it] ?: it })

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): MethodCallQ {
    val args = args.map { it.renameUsage(currIndex) }
    val self = self.renameUsage(currIndex)
    val to = to.renameDefinition(currIndex, updateIndex)
    return MethodCallQ(to, self, ident, args, argsTypes)
  }
}

data class FunCodeLabelQ(override val label: Label, val args: List<ArgValue>) : Quadruple, Labeled {
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple = this
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): FunCodeLabelQ {
    val args = args.map { it.renameDefinition(currIndex, updateIndex) as ArgValue }
    return FunCodeLabelQ(label, args)
  }
}

data class CodeLabelQ(override val label: Label) : Quadruple, Labeled {
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple = this
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple = this
}

data class CondJumpQ(val cond: ValueHolder, override val toLabel: Label) : Quadruple, Jumping {
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(cond = constants[cond] ?: cond)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): CondJumpQ {
    val cond = cond.renameUsage(currIndex)
    return CondJumpQ(cond, toLabel)
  }
}

data class RelCondJumpQ(val left: ValueHolder, val op: RelOp, val right: ValueHolder, override val toLabel: Label) :
  Quadruple, Jumping {
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(left = constants[left] ?: left, right = constants[right] ?: right)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RelCondJumpQ {
    val right = right.renameUsage(currIndex)
    val left = left.renameUsage(currIndex)
    return RelCondJumpQ(left, op, right, toLabel)
  }
}

data class JumpQ(override val toLabel: Label) : Quadruple, Jumping {
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple = this
  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple = this
}

data class RetQ(val value: ValueHolder? = null) : Quadruple, Jumping {
  override val toLabel: Label? = null
  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): Quadruple =
    copy(value = constants[value] ?: value)

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): RetQ {
    val value = value?.renameUsage(currIndex)
    return RetQ(value)
  }
}

data class PhonyQ(
  val to: VirtualReg,
  private val original: VirtualReg,
  val from: Map<Label, ValueHolder> = HashMap(),
) : Quadruple {
  fun renameDefinition(currIndex: CurrIndex, updateIndex: UpdateIndex): PhonyQ =
    if (to == original) copy(
      to = to.renameDefinition(currIndex, updateIndex),
      original = original,
      from = HashMap(from)
    ) else err("Cannot rename Phony multiple times")

  fun renamePathUsage(label: Label, currIndex: CurrIndex): PhonyQ = copy(
    to = to, original = original, from = from + (label to original.renameUsage(currIndex))
  )

  override fun propagateConstants(constants: Map<VirtualReg, ConstValue>): PhonyQ = copy(
    to = to, original = original, from = from.mapValues { constants[it.value] ?: it.value }
  )

  override fun rename(currIndex: CurrIndex, updateIndex: UpdateIndex): Quadruple =
    err("Cannot rename PhonyQ in single step - rename usages and definitions separately")
}

private fun <T> err(message: String): T = throw IRException(message.msg)
