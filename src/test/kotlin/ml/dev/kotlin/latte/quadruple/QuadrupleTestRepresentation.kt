package ml.dev.kotlin.latte.quadruple

internal fun ValueHolder.repr(): String = when (this) {
  is BooleanConstValue -> "$bool"
  is IntConstValue -> "$int"
  is StringConstValue -> label.name
  is ArgValue -> name
  is LocalValue -> name
  is TempValue -> name
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
  is DecQ -> "${to.repr()} = ${from.repr()} - 1"
  is IncQ -> "${to.repr()} = ${from.repr()} + 1"
}.let { if (this is LabelQ) it else "  $it" }
