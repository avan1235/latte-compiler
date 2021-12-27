package ml.dev.kotlin.latte.util

import java.util.*

private typealias StackLevel = UInt

data class StackTable<K, V>(
  private var level: StackLevel = 0U,
  private val levelValues: MutableDefaultMap<K, Stack<V>> = MutableDefaultMap({ Stack() }),
  private val levelNames: MutableDefaultMap<StackLevel, MutableSet<K>> = MutableDefaultMap({ hashSetOf() }),
) {
  val currentLevelNames: Set<K> get() = levelNames[level]

  operator fun get(key: K): V? = levelValues[key].lastOrNull()
  operator fun set(key: K, value: V) {
    if (key in currentLevelNames) throw IllegalArgumentException("Cannot redefine $key with $value for $this")
    levelValues[key] += value
    levelNames[level] += key
  }

  fun beginLevel() {
    level += 1U
  }

  fun endLevel(): List<Pair<K, V>> {
    if (level == 0U) throw IllegalStateException("Ended too many levels for $this")
    val levelNames = levelNames.remove(level)
    val levelValues = levelNames.map { it to levelValues[it].pop() }
    level -= 1U
    return levelValues
  }

  inline fun <U> level(action: () -> U): U {
    beginLevel()
    val result = action()
    endLevel()
    return result
  }

  override fun toString(): String = "StackTable(level=$level, levelValues=$levelValues, levelNames=$levelNames)"
}
