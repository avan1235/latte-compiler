package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.util.FrontendException
import ml.dev.kotlin.latte.util.eprintln
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
    println(program)
    eprintln(exception.userMessage)
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
//        .filter { it.name.contains("linked") }
  }
}
