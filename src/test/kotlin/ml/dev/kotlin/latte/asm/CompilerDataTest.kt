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
  fun `should work with arithmetic on different locations`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        int x = f(42, 24);
        printInt(x);
        return 0;
      }
      int f(int a, int b) {
        int x = 2;
        int y = 3;
        printInt(a / b);
        printInt(a / x);
        printInt(a / 3);
        printInt(b / a);
        printInt(b / x);
        printInt(b / 3);
        printInt(85 / a);
        printInt(85 / x);
        printInt(85 / 3);

        printInt(a % b);
        printInt(a % x);
        printInt(a % 3);
        printInt(b % a);
        printInt(b % x);
        printInt(b % 3);
        printInt(85 % a);
        printInt(85 % x);
        printInt(85 % 3);

        printInt(a + b);
        printInt(a + x);
        printInt(a + 3);
        printInt(b + a);
        printInt(b + x);
        printInt(b + 3);
        printInt(85 + a);
        printInt(85 + x);
        printInt(85 + 3);

        printInt(a - b);
        printInt(a - x);
        printInt(a - 3);
        printInt(b - a);
        printInt(b - x);
        printInt(b - 3);
        printInt(85 - a);
        printInt(85 - x);
        printInt(85 - 3);

        printInt(a * b);
        printInt(a * x);
        printInt(a * 3);
        printInt(b * a);
        printInt(b * x);
        printInt(b * 3);
        printInt(85 * a);
        printInt(85 * x);
        printInt(85 * 3);

        return a + b * x - y;
      }
      """,
      output = """
      1
      21
      14
      0
      12
      8
      2
      42
      28
      18
      0
      0
      24
      0
      0
      1
      1
      1
      66
      44
      45
      66
      26
      27
      127
      87
      88
      18
      40
      39
      -18
      22
      21
      43
      83
      82
      1008
      84
      126
      1008
      48
      72
      3570
      170
      255
      87

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
