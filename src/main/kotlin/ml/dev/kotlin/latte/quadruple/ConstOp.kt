package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.util.IRException
import ml.dev.kotlin.latte.util.msg

inline val String.label: Label get() = Label(this)

inline val String.int get() = IntConstValue(toIntOrNull() ?: throw IRException("Int value doesn't into memory".msg))
inline val Boolean.bool get() = BooleanConstValue(this)
inline val String.str get() = StringConstValue(this)

operator fun IntConstValue.unaryMinus() = IntConstValue(-int)
operator fun BooleanConstValue.not() = BooleanConstValue(!bool)
operator fun IntConstValue.plus(o: IntConstValue) = IntConstValue(int + o.int)
operator fun IntConstValue.minus(o: IntConstValue) = IntConstValue(int - o.int)
operator fun IntConstValue.times(o: IntConstValue) = IntConstValue(int * o.int)
operator fun IntConstValue.div(o: IntConstValue) = IntConstValue(int / o.int)
operator fun IntConstValue.rem(o: IntConstValue) = IntConstValue(int % o.int)
operator fun IntConstValue.compareTo(o: IntConstValue) = int.compareTo(o.int)
operator fun BooleanConstValue.compareTo(o: BooleanConstValue) = bool.compareTo(o.bool)
operator fun StringConstValue.plus(o: StringConstValue) = StringConstValue(str + o.str)
