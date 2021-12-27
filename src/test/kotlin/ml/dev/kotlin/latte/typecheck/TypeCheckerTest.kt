package ml.dev.kotlin.latte.typecheck

import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.util.FrontendException
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.*
import java.util.stream.Stream

internal class TypeCheckerTest {

  @ParameterizedTest
  @MethodSource("goodExamplesProvider")
  fun `should accept valid input files`(input: File) {
    input.inputStream().parse().typeCheck()
  }

  @ParameterizedTest
  @MethodSource("badExamplesProvider")
  fun `should throw on invalid input files`(input: File) {
    val exception = assertThrows<FrontendException> { input.inputStream().parse().typeCheck() }
    println(exception.userMessage)
  }

  companion object {
    @JvmStatic
    fun goodExamplesProvider(): Stream<File> = File("src/test/resources/good").testLatteFilesStream()

    @JvmStatic
    fun badExamplesProvider(): Stream<File> = File("src/test/resources/bad").testLatteFilesStream()

    private fun File.testLatteFilesStream() = listFiles().let { Arrays.stream(it) }.filter { it.extension == "lat" }
  }
}
