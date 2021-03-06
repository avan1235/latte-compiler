package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.invoke
import ml.dev.kotlin.latte.util.withExtension
import ml.dev.kotlin.latte.util.zeroCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class CompilerFileTest {

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept good examples and match their output with default allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.DEFAULT)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept good examples and match their output with first allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.FIRST)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept good examples and match their output with last allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.LAST)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept good examples and match their output with random allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.RANDOM)

  @ParameterizedTest
  @MethodSource("extensionsExamplesProvider")
  fun `should accept extensions examples and match their output with default allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.DEFAULT)

  @ParameterizedTest
  @MethodSource("extensionsExamplesProvider")
  fun `should accept extensions examples and match their output with first allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.FIRST)

  @ParameterizedTest
  @MethodSource("extensionsExamplesProvider")
  fun `should accept extensions examples and match their output with last allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.LAST)

  @ParameterizedTest
  @MethodSource("extensionsExamplesProvider")
  fun `should accept extensions examples and match their output with random allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.RANDOM)

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

    @JvmStatic
    fun extensionsExamplesProvider(): Stream<File> = File("src/test/resources/extensions").testLatteFilesStream()

    private fun File.testLatteFilesStream(): Stream<File> =
      StreamSupport.stream(walkTopDown().toList().spliterator(), false).filter { it.isFile && it.extension == "lat" }
  }
}

private fun testCompilerWithAllocatorStrategy(
  input: File,
  allocator: TestAllocator,
  removeOutputs: Boolean = true,
) {
  val shortcut = allocator.name.lowercase()
  val expected = input.withExtension(".output").readText()
  val inputFile = input.withExtension(".input").takeIf { it.exists() }
  val compiled = input.runCompiler(strategy = allocator.strategy)
  val asmFile = input.withExtension(".${shortcut}.s").apply { writeText(compiled) }
  val (o, exe) = asm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = input.withExtension(".${shortcut}.outputTest").apply { createNewFile() }
  val errFile = input.withExtension(".${shortcut}.errorTest").apply { createNewFile() }
  exe.absolutePath(inputFile, outFile, errFile).zeroCode()
  assertEquals(expected, outFile.readText())
  assertEquals("", errFile.readText())
  if (removeOutputs) listOf(asmFile, o, exe, outFile, errFile).forEach { it.delete() }
}
