package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Files
import java.util.stream.Stream

internal class CompilerDataTest {

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should work with function that have multiple args`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        int x = f(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        printInt(x);
        return 0;
      }
      int f(int a, int b, int c, int d, int e, int f, int g, int h,
            int i, int j, int k, int l, int m, int n, int o, int p) {
        return a + b + c + d + e + f + g + h + i + j + k + l + m + n + o + p;
      }
      """,
      output = """
      120

      """
    )


  companion object {
    @JvmStatic
    fun allocatorsProvider(): Stream<TestAllocator> = Stream.of(*TestAllocator.values())
  }
}

private fun testCompilerWithAllocatorStrategy(
  allocator: TestAllocator,
  program: String,
  output: String = "",
  input: String? = null,
) {
  val shortcut = allocator.name.lowercase()
  val dataDir = File("testData/").apply { mkdirs() }.dir.toPath()
  val programFile = Files.createTempFile(dataDir, shortcut, ".lat").toFile().apply { writeText(program) }
  val inputFile = input?.let { programFile.withExtension(".input", it.trimIndent()) }
  val compiled = programFile.runCompiler(allocator.strategy)
  val asmFile = programFile.withExtension(".asm", compiled)
  val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = programFile.withExtension(".outputTest")
  val errFile = programFile.withExtension(".errorTest")
  exe.absolutePath(inputFile, outFile, errFile)
  assertEquals(output.trimIndent(), outFile.readText())
  assertEquals("", errFile.readText())
  listOfNotNull(programFile, inputFile, asmFile, o, exe, outFile, errFile).forEach { it.delete() }
}

private fun File.withExtension(ext: String, data: String? = null): File =
  dir.resolve("${nameWithoutExtension}$ext").apply { createNewFile() }.apply { data?.let { writeText(it) } }
