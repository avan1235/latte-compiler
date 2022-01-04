package ml.dev.kotlin.latte.util

import java.util.*

typealias StackLevel = Int

data class StackTable<K, V>(
  private var _level: StackLevel = 0,
  private val levelValues: MutableDefaultMap<K, Stack<V>> = MutableDefaultMap({ Stack() }),
  private val levelNames: MutableDefaultMap<StackLevel, MutableSet<K>> = MutableDefaultMap(withSet()),
) {
  val currentLevelNames: Set<K> get() = levelNames[_level]

  operator fun get(key: K): V? = levelValues[key].lastOrNull()
  operator fun set(key: K, value: V) {
    if (key in currentLevelNames) throw IllegalArgumentException("Cannot redefine $key with $value for $this")
    levelValues[key] += value
    levelNames[_level] += key
  }

  fun beginLevel() {
    _level += 1
  }

  fun endLevel(): List<Pair<K, V>> {
    if (_level == 0) throw IllegalStateException("Ended too many levels for $this")
    val levelNames = levelNames.remove(_level)
    val levelValues = levelNames.map { it to levelValues[it].pop() }
    _level -= 1
    return levelValues
  }

  inline fun <U> onLevel(action: () -> U): U {
    beginLevel()
    val result = action()
    endLevel()
    return result
  }
}
