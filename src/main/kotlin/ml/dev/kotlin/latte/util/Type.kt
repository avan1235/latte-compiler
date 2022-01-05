package ml.dev.kotlin.latte.util

@Suppress("NOTHING_TO_INLINE")
inline fun Any?.unit() = Unit

inline fun <U> Any?.then(action: () -> U): U = action()
