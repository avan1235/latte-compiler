package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.DEFAULT_ALLOCATOR_STRATEGY
import ml.dev.kotlin.latte.quadruple.VirtualReg
import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class CompilerTest {

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with default allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, name = "default", strategy = DEFAULT_ALLOCATOR_STRATEGY)

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with first allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, name = "first") { analysis, manager ->
      object : AllocatorStrategy(analysis, manager) {
        override fun selectToSplit(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg =
          withEdges.values.flatten().first()

        override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<VirtualReg, VarLoc>): Reg =
          available.first()
      }
    }

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with last allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, name = "last") { analysis, manager ->
      object : AllocatorStrategy(analysis, manager) {
        override fun selectToSplit(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg =
          withEdges.values.flatten().last()

        override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<VirtualReg, VarLoc>): Reg =
          available.last()
      }
    }

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output with random allocator`(input: File) =
    testCompilerWithAllocatorStrategy(input, name = "random") { analysis, manager ->
      object : AllocatorStrategy(analysis, manager) {
        override fun selectToSplit(withEdges: TreeMap<Int, HashSet<VirtualReg>>): VirtualReg =
          withEdges.values.flatten().random()

        override fun selectColor(virtualReg: VirtualReg, available: Set<Reg>, coloring: Map<VirtualReg, VarLoc>): Reg =
          available.random()
      }
    }

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

    private fun File.testLatteFilesStream(): Stream<File> =
      StreamSupport.stream(walkTopDown().toList().spliterator(), false).filter { it.isFile && it.extension == "lat" }
  }
}

private fun testCompilerWithAllocatorStrategy(
  input: File,
  name: String,
  removeOutputs: Boolean = true,
  strategy: AllocatorStrategyProducer
) {
  val expected = File(input.dir, "${input.nameWithoutExtension}.output").readText()
  val inputFile = File(input.dir, "${input.nameWithoutExtension}.input").takeIf { it.exists() }
  val compiled = input.runCompiler(strategy)
  val asmFile = File(input.dir, "${input.nameWithoutExtension}.${name}.asm").apply { writeText(compiled) }
  val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = File(input.dir, "${input.nameWithoutExtension}.${name}.outputTest").apply { createNewFile() }
  val errFile = File(input.dir, "${input.nameWithoutExtension}.${name}.errorTest").apply { createNewFile() }
  exe.absolutePath(inputFile, outFile, errFile)
  assertEquals(expected, outFile.readText())
  assertEquals("", errFile.readText())
  if (removeOutputs) listOf(asmFile, o, exe, outFile, errFile).forEach { it.delete() }
}
