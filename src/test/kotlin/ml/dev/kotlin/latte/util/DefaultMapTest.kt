package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class DefaultMapTest {

  private val idGenerator = { id: Int -> id }
  private val setGenerator = { _: Int -> HashSet<Int>() }

  @Test
  fun `returns proper value on key`() {
    val map = MutableDefaultMap(idGenerator)
    assertEquals(map[0], 0)
    assertEquals(map[42], 42)
    assertEquals(map[-42], -42)
  }

  @Test
  fun `sets value after returning default`() {
    val map = MutableDefaultMap(idGenerator)
    assertEquals(42 in map, false)
    assertEquals(map[42], 42)
    assertEquals(42 in map, true)
  }

  @Test
  fun `returns generated value on different keys`() {
    val map = MutableDefaultMap(setGenerator)
    assertNotSame(map[0], map[1])
  }


  @Test
  fun `deep copies with no default values`() {
    val map = MutableDefaultMap(setGenerator)
    map[0] += 42
    map[0] += 24
    assertEquals(map[0], setOf(24, 42))
    assertEquals(map[2], setOf<Int>())

    val copy = map.deepCopy { HashSet(it) }

    assertNotSame(map[0], copy[0])
    assertEquals(map, copy)

    assertNotSame(map[2], copy[2])
    assertEquals(map, copy)
  }
}
