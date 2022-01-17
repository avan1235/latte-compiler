package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class CollectionsTest {

  @Nested
  inner class CombinationsTest {
    @Test
    fun `should output all possible combinations`() {
      val given = listOf(
        listOf(1, 2, 3),
        listOf(4, 5),
        listOf(6, 7, 8, 9),
        listOf(1)
      )
      val expected = setOf(
        listOf(1, 4, 6, 1),
        listOf(1, 4, 7, 1),
        listOf(1, 4, 8, 1),
        listOf(1, 4, 9, 1),
        listOf(1, 5, 6, 1),
        listOf(1, 5, 7, 1),
        listOf(1, 5, 8, 1),
        listOf(1, 5, 9, 1),
        listOf(2, 4, 6, 1),
        listOf(2, 4, 7, 1),
        listOf(2, 4, 8, 1),
        listOf(2, 4, 9, 1),
        listOf(2, 5, 6, 1),
        listOf(2, 5, 7, 1),
        listOf(2, 5, 8, 1),
        listOf(2, 5, 9, 1),
        listOf(3, 4, 6, 1),
        listOf(3, 4, 7, 1),
        listOf(3, 4, 8, 1),
        listOf(3, 4, 9, 1),
        listOf(3, 5, 6, 1),
        listOf(3, 5, 7, 1),
        listOf(3, 5, 8, 1),
        listOf(3, 5, 9, 1),
      )
      val transposed = given.combinations().toSet()
      assertEquals(expected, transposed)
    }

    @Test
    fun `should return set with empty list on empty list given`() {
      val given = listOf<List<Int>>()
      val expected = setOf(emptyList<Int>())
      val transposed = given.combinations().toSet()
      assertEquals(expected, transposed)
    }

    @Test
    fun `should return empty set with empty list on list with empty lists given`() {
      val given = listOf<List<Int>>(emptyList(), emptyList(), emptyList())
      val expected = setOf<List<Int>>()
      val transposed = given.combinations().toSet()
      assertEquals(expected, transposed)
    }
  }

  @Nested
  inner class SplitAtTest {
    @Test
    fun `should split on last element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2), listOf(3)),
      iterable = listOf(1, 2, 3),
      last = { it == 2 },
    )

    @Test
    fun `should split on first element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2), listOf(1, 3)),
      iterable = listOf(1, 2, 1, 3),
      first = { it == 1 },
    )

    @Test
    fun `should split on first and last element`() = testSplitAtLast(
      expected = listOf(listOf(1, 2, 2, 3), listOf(1, 4, 4, 3), listOf(1), listOf(1, 3), listOf(3)),
      iterable = listOf(1, 2, 2, 3, 1, 4, 4, 3, 1, 1, 3, 3),
      first = { it == 1 },
      last = { it == 3 },
    )

    @Test
    fun `should split on first and last single element`() = testSplitAtLast(
      expected = listOf(listOf(1), listOf(1), listOf(1), listOf(1)),
      iterable = listOf(1, 1, 1, 1),
      first = { it == 1 },
      last = { it == 1 },
    )
  }
}

private fun <T> testSplitAtLast(
  expected: List<List<T>>,
  iterable: Iterable<T>,
  first: (T) -> Boolean = { false },
  last: (T) -> Boolean = { false },
): Unit = assertEquals(expected, iterable.splitAt(first, last).toList())
