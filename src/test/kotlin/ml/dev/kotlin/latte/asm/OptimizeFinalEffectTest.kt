package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
import ml.dev.kotlin.latte.util.withExtension
import ml.dev.kotlin.latte.util.zeroCode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

internal class OptimizeFinalEffectTest {
  @Test
  fun `optimize constants propagation`() = testCompilerOptimizedAndNot(
    program = """
    int main() {
      int x = 5;
      int y = 1;
      while (y < x) {
        printInt(y);
        y++;
      }
      return 0;
    }
    """,
    output = """
    1
    2
    3
    4

    """,
    propagateConstants = true,
  )

  @Test
  fun `optimize simplify arithmetic expressions`() = testCompilerOptimizedAndNot(
    program = """
    int main() {
      int x = 5;
      int y = 1;
      int z = x + y;
      printInt(z);
      z = x - y;
      printInt(z);
      z = x * y;
      printInt(z);
      z = x / y;
      printInt(z);

      return 0;
    }
    """,
    output = """
    6
    4
    5
    5

    """,
    simplifyExpr = true,
    propagateConstants = true
  )

  @Test
  fun `optimize common subexpressions in lcse`() = testCompilerOptimizedAndNot(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
      f(a, b);
      return 0;
    }
    void f(int a, int b) {
      int c = (a + b) * (a - b);
      int d = (a + b) / (a - b);
      if (c < d) {
        printInt(c);
        printInt(d);
      }
      else {
        printInt(d);
        printInt(c);
      }
    }
    """,
    input = """
    5
    3

    """,
    output = """
    4
    16

    """,
    lcse = true,
    propagateConstants = true,
  )
}

private fun testCompilerOptimizedAndNot(
  program: String,
  propagateConstants: Boolean = false,
  simplifyExpr: Boolean = false,
  lcse: Boolean = false,
  input: String? = null,
  output: String = "",
) {
  val notOptimized = configuredRunCompiler(
    program,
    propagateConstants = false,
    simplifyExpr = false,
    lcse = false,
    input,
    output
  )
  val optimized = configuredRunCompiler(
    program,
    propagateConstants,
    simplifyExpr,
    lcse,
    input,
    output
  )
  assertTrue(
    notOptimized.lines().size > optimized.lines().size,
    "Expected optimized code to have less instructions but got\n${optimized}while before optimize\n\n${notOptimized}"
  )
}

private fun configuredRunCompiler(
  program: String,
  propagateConstants: Boolean,
  simplifyExpr: Boolean,
  lcse: Boolean,
  input: String?,
  output: String,
): String {
  val dataDir = File("testData/").apply { mkdirs() }.dir.toPath()
  val programFile = Files.createTempFile(dataDir, "opt", ".lat").toFile().apply { writeText(program) }
  val inputFile = input?.let { programFile.withExtension(".input", it.trimIndent()) }
  val code =
    programFile.runCompiler(removeTempDefs = true, propagateConstants, simplifyExpr, removeDeadAssignQ = true, lcse)
  val asmFile = programFile.withExtension(".asm", code)
  val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = programFile.withExtension(".outputTest")
  val errFile = programFile.withExtension(".errorTest")
  exe.absolutePath(inputFile, outFile, errFile).zeroCode()
  Assertions.assertEquals(output.trimIndent(), outFile.readText())
  Assertions.assertEquals("", errFile.readText())
  listOfNotNull(programFile, inputFile, asmFile, o, exe, outFile).forEach { it.delete() }
  return code
}
