package ml.dev.kotlin.latte.quadruple

fun Quadruple.definedVars(): Sequence<VirtualReg> = when (this) {
  is AssignQ -> sequenceOf(to)
  is BinOpQ -> sequenceOf(to)
  is UnOpQ -> sequenceOf(to)
  is UnOpModQ -> sequenceOf(to)
  is FunCallQ -> sequenceOf(to)
  is PhonyQ -> sequenceOf(to)
  is FunCodeLabelQ -> args.asSequence()
  is RelCondJumpQ -> emptySequence()
  is CondJumpQ -> emptySequence()
  is RetQ -> emptySequence()
  is JumpQ -> emptySequence()
  is CodeLabelQ -> emptySequence()
}

fun Quadruple.usedVars(): Sequence<VirtualReg> = when (this) {
  is AssignQ -> sequenceOf(from as? VirtualReg)
  is BinOpQ -> sequenceOf(left, right as? VirtualReg)
  is UnOpQ -> sequenceOf(from)
  is UnOpModQ -> sequenceOf(from)
  is FunCallQ -> args.asSequence().filterIsInstance<VirtualReg>()
  is PhonyQ -> from.values.asSequence().filterIsInstance<VirtualReg>()
  is RelCondJumpQ -> sequenceOf(left, right as? VirtualReg)
  is CondJumpQ -> sequenceOf(cond)
  is RetQ -> sequenceOf(value as? VirtualReg)
  is FunCodeLabelQ -> emptySequence()
  is CodeLabelQ -> emptySequence()
  is JumpQ -> emptySequence()
}.filterNotNull()
