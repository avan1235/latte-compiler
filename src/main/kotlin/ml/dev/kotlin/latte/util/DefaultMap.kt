package ml.dev.kotlin.latte.util

class MutableDefaultMap<K, V>(
  private val defaultValue: (K) -> V,
  private val map: MutableMap<K, V> = hashMapOf()
) : DefaultMap<K, V>, MutableMap<K, V> by map {
  override fun get(key: K): V = map.getOrDefault(key, defaultValue(key)).also { this[key] = it }
  override fun remove(key: K): V = map.remove(key) ?: defaultValue(key)
  override fun toString() = "MutableDefaultMap(map=$map)"
  override fun hashCode() = map.hashCode()
  override fun equals(other: Any?): Boolean = (other as? MutableDefaultMap<*, *>)?.let { it.map == map } ?: false
}

interface DefaultMap<K, out V> : Map<K, V> {
  override fun get(key: K): V
}
