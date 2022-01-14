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
      int c = (a + b) * (a - b);
      int d = (a + b) / (a - b);
      int e = (a + b) % (a - b);
      if (c < d) {
        printInt(c);
        printInt(d);
        printInt(e);
      }
      else {
        printInt(d);
        printInt(e);
        printInt(c);
      }
      return 0;
    }
    """,
    input = """
    5
    3

    """,
    output = """
    4
    0
    16

    """,
    lcse = true,
  )

  @Test
  fun `optimize common subexpressions in gcse`() = testCompilerOptimizedAndNot(
    program = """
    int main() {
      int a = readInt();
      int b = readInt();
      int c = (a * b) * (a + b);
      while (c > 118) {
        int d = (a * b) * (a + b);
        printInt(d);
        printInt(c);
        c--;
      }
      int d = (a * b) * (a + b);
      printInt(d);
      return 0;
    }
    """,
    input = """
    3
    5

    """,
    output = """
    120
    120
    120
    119
    120

    """,
    gcse = true,
  )
}

private fun testCompilerOptimizedAndNot(
  program: String,
  propagateConstants: Boolean = false,
  simplifyExpr: Boolean = false,
  gcse: Boolean = false,
  lcse: Boolean = false,
  input: String? = null,
  output: String = "",
) {
  val notOptimized = configuredRunCompiler(
    program,
    propagateConstants = false,
    simplifyExpr = false,
    gcse = false,
    lcse = false,
    input,
    output
  )
  val optimized = configuredRunCompiler(
    program,
    propagateConstants,
    simplifyExpr,
    gcse,
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
  gcse: Boolean,
  lcse: Boolean,
  input: String?,
  output: String,
): String {
  val mask = listOf(propagateConstants, simplifyExpr, gcse, lcse).joinToString("") { if (it) " 1" else "0" }
  val shortcut = "opt.$mask"
  val dataDir = File("testData/").apply { mkdirs() }.dir.toPath()
  val programFile = Files.createTempFile(dataDir, shortcut, ".lat").toFile().apply { writeText(program) }
  val inputFile = input?.let { programFile.withExtension(".input", it.trimIndent()) }
  val code = programFile.runCompiler(true, propagateConstants, simplifyExpr, true, gcse, lcse)
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
