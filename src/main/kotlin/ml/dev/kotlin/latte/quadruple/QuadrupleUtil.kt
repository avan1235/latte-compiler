package ml.dev.kotlin.latte.quadruple

fun Quadruple.definedVars(): Sequence<VirtualReg> = when (this) {
  is AssignQ -> sequenceOf(to)
  is BinOpQ -> sequenceOf(to)
  is UnOpQ -> sequenceOf(to)
  is UnOpModQ -> sequenceOf(to)
  is FunCallQ -> sequenceOf(to)
  is PhonyQ -> sequenceOf(to)
  is LoadQ -> sequenceOf(to)
  is FunCodeLabelQ -> args.asSequence()
  is StoreQ -> emptySequence()
  is RelCondJumpQ -> emptySequence()
  is CondJumpQ -> emptySequence()
  is RetQ -> emptySequence()
  is JumpQ -> emptySequence()
  is CodeLabelQ -> emptySequence()
}

fun Quadruple.usedVars(): Sequence<VirtualReg> = when (this) {
  is AssignQ -> sequenceOf(from as? VirtualReg)
  is BinOpQ -> sequenceOf(left as? VirtualReg, right as? VirtualReg)
  is UnOpQ -> sequenceOf(from as? VirtualReg)
  is UnOpModQ -> sequenceOf(from)
  is FunCallQ -> args.asSequence().filterIsInstance<VirtualReg>()
  is PhonyQ -> from.values.asSequence().filterIsInstance<VirtualReg>()
  is RelCondJumpQ -> sequenceOf(left as? VirtualReg, right as? VirtualReg)
  is CondJumpQ -> sequenceOf(cond as? VirtualReg)
  is RetQ -> sequenceOf(value as? VirtualReg)
  is LoadQ -> sequenceOf(from as? VirtualReg)
  is StoreQ -> sequenceOf(to as? VirtualReg, from as? VirtualReg)
  is FunCodeLabelQ -> emptySequence()
  is CodeLabelQ -> emptySequence()
  is JumpQ -> emptySequence()
}.filterNotNull()

internal fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> "$int"
  is StringConstValue -> label.name
  is ArgValue -> id
  is LocalValue -> id
  is NullConstValue -> "null"
}

internal fun Quadruple.repr(): String = when (this) {
  is AssignQ -> "${to.repr()} = ${from.repr()}"
  is RelCondJumpQ -> "if ${left.repr()} ${op.name.lowercase()} ${right.repr()} goto ${toLabel.name}"
  is BinOpQ -> "${to.repr()} = ${left.repr()} ${op.name.lowercase()} ${right.repr()}"
  is UnOpQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is FunCodeLabelQ -> "${label.name}(${args.joinToString { it.repr() }}):"
  is CodeLabelQ -> "${label.name}:"
  is CondJumpQ -> "if ${cond.repr()} goto ${toLabel.name}"
  is JumpQ -> "goto ${toLabel.name}"
  is FunCallQ -> "${to.repr()} = call ${label.name} (${args.joinToString { it.repr() }})"
  is RetQ -> "ret${value?.let { " ${it.repr()}" } ?: ""}"
  is UnOpModQ -> "${to.repr()} = ${op.name.lowercase()} ${from.repr()}"
  is PhonyQ -> "${to.repr()} = phi (${from.toList().joinToString(", ") { "${it.first.name}:${it.second.repr()}" }})"
  is LoadQ -> "${to.repr()} = *(${from.repr()} + $offset)"
  is StoreQ -> "*(${to.repr()} + $offset) = ${from.repr()}"
}.let { if (this is Labeled) it else "  $it" }

internal fun Iterable<Quadruple>.isSSA(): Boolean =
  flatMap { it.definedVars() }.map { it.repr() }.let { it.size == it.toHashSet().size }

internal fun ControlFlowGraph.instructions(): Sequence<Quadruple> =
  orderedBlocks().asSequence().flatMap { it.statements }

