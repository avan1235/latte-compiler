package ml.dev.kotlin.latte.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class CollectionsTest {

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

