package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
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
  fun `should accept valid input files and match their output with default allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.DEFAULT)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with first allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.FIRST)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with last allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.LAST)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with random allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, TestAllocator.RANDOM)

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

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
  val expected = File(input.dir, "${input.nameWithoutExtension}.output").readText()
  val inputFile = File(input.dir, "${input.nameWithoutExtension}.input").takeIf { it.exists() }
  val compiled = input.runCompiler(allocator.strategy)
  val asmFile = File(input.dir, "${input.nameWithoutExtension}.${shortcut}.asm").apply { writeText(compiled) }
  val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = File(input.dir, "${input.nameWithoutExtension}.${shortcut}.outputTest").apply { createNewFile() }
  val errFile = File(input.dir, "${input.nameWithoutExtension}.${shortcut}.errorTest").apply { createNewFile() }
  exe.absolutePath(inputFile, outFile, errFile).zeroCode()
  assertEquals(expected, outFile.readText())
  assertEquals("", errFile.readText())
  if (removeOutputs) listOf(asmFile, o, exe, outFile, errFile).forEach { it.delete() }
}
