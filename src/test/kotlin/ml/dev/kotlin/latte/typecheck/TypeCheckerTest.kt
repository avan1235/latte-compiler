package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.util.FrontendException
import ml.dev.kotlin.latte.util.LocalizedMessage
import ml.dev.kotlin.latte.util.eprintln
import ml.dev.kotlin.latte.util.unit
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal class TypeCheckerTest {

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files`(input: File) {
    input.inputStream().parse().typeCheck()
  }

  @ParameterizedTest
  @MethodSource("extensionsExamplesProvider")
  fun `should accept valid extension input files`(input: File) {
    input.inputStream().parse().typeCheck()
  }

  @ParameterizedTest
  @MethodSource("badExamplesProvider")
  fun `should throw on invalid input files`(input: File) {
    val program = input.readText()
    val exception = assertThrows<FrontendException> { program.byteInputStream().parse().typeCheck() }
    logReportedError(program, exception.userMessage)
  }

  @Nested
  inner class ExplicitTypeCheckerTest {
    @Test
    fun `test matching functions by signatures`() = testTypeCheckerOn(
      program = """
      int main () {
        A a = new A;
        B b = new B;
        C c = new C;
        checkMeAAA(a, b, c);
        checkMeABC(a, b, c);
        return 0;
      }

      class A {
        int x;
      }

      class B extends A {
        int y;
      }

      class C extends B {
        int z;
      }

      void checkMeAAA(A x, A y, A z) {
        printInt(x.x);
        printInt(y.x);
        printInt(z.x);
      }

      void checkMeABC(A x, B y, C z) {
        printInt(x.x);
        printInt(y.y);
        printInt(z.z);
      }
      """
    )
  }

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

    @JvmStatic
    fun extensionsExamplesProvider(): Stream<File> = File("src/test/resources/extensions").testLatteFilesStream()

    @JvmStatic
    fun badExamplesProvider(): Stream<File> = File("src/test/resources/bad").testLatteFilesStream()

    private fun File.testLatteFilesStream(): Stream<File> =
      StreamSupport.stream(walkTopDown().toList().spliterator(), false).filter { it.isFile && it.extension == "lat" }
  }
}

private fun testTypeCheckerOn(program: String): Unit = program.byteInputStream().parse().typeCheck().unit()

private fun logReportedError(program: String, message: LocalizedMessage): Unit = synchronized(System.err) {
  eprintln(program)
  eprintln(message)
}
