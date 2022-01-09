package ml.dev.kotlin.latte.util

typealias StackLevel = Int

data class StackTable<K, V>(
  private var leve: StackLevel = 0,
  private val levelValues: MutableDefaultMap<K, ArrayDeque<V>> = MutableDefaultMap({ ArrayDeque() }),
  private val levelNames: MutableDefaultMap<StackLevel, MutableSet<K>> = MutableDefaultMap(withSet()),
) {
  val currentLevelNames: Set<K> get() = levelNames[leve]

  operator fun get(key: K): V? = levelValues[key].lastOrNull()
  operator fun set(key: K, value: V) {
    if (key in currentLevelNames) throw LatteIllegalStateException("Cannot redefine $key with $value for $this".msg)
    levelValues[key] += value
    levelNames[leve] += key
  }

  fun beginLevel() {
    leve += 1
  }

  fun endLevel(): List<Pair<K, V>> {
    if (leve == 0) throw LatteIllegalStateException("Ended too many levels for $this".msg)
    val levelNames = levelNames.remove(leve)
    val levelValues = levelNames.map { it to levelValues[it].removeLast() }
    leve -= 1
    return levelValues
  }

  inline fun <U> onLevel(action: () -> U): U {
    beginLevel()
    val result = action()
    endLevel()
    return result
  }
}
