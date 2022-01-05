package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class CompilerTest {

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files and match their output`(input: File) {
    val expected = File(input.dir, "${input.nameWithoutExtension}.output").readText()
    val inputFile = File(input.dir, "${input.nameWithoutExtension}.input").takeIf { it.exists() }
    val compiled = input.runCompiler()
    val asmFile = File(input.dir, "${input.nameWithoutExtension}.asm").apply { writeText(compiled) }
    val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
    val outFile = File(input.dir, "${input.nameWithoutExtension}.outputTest").apply { createNewFile() }
    val errFile = File(input.dir, "${input.nameWithoutExtension}.errorTest").apply { createNewFile() }
    exe.absolutePath(inputFile, outFile, errFile)
    assertEquals(expected, outFile.readText())
    assertEquals("", errFile.readText())
    listOf(asmFile, o, exe, outFile, errFile).forEach { it.delete() }
  }

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

    private fun File.testLatteFilesStream(): Stream<File> =
      StreamSupport.stream(walkTopDown().toList().spliterator(), false).filter { it.isFile && it.extension == "lat" }
  }
}
