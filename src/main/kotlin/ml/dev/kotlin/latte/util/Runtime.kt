package ml.dev.kotlin.latte.util

import java.io.File
import java.nio.file.Files.createTempFile

operator fun String.invoke(
  inputFile: File? = null,
  outFile: File? = null,
  errFile: File? = null,
  workingDir: File = exeFile()
): Int {
  val input = inputFile ?: createTempFile(workingDir.dir.toPath(), null, null).toFile()
  val out = outFile ?: createTempFile(workingDir.dir.toPath(), null, null).toFile()
  val err = outFile ?: createTempFile(workingDir.dir.toPath(), null, null).toFile()
  return try {
    ProcessBuilder(*split(" ").toTypedArray()).directory(workingDir.dir)
      .redirectInput(input)
      .redirectOutput(out)
      .redirectError(err)
      .start()
      .waitFor().also {
        out.readText().let { if (it.isNotEmpty()) println(it) }
        err.readText().let { if (it.isNotEmpty()) eprintln(it) }
      }
  } catch (e: Throwable) {
    eprintln(e.message)
    -1
  } finally {
    if (inputFile == null) input.delete()
    if (outFile == null) out.delete()
    if (errFile == null) err.delete()
  }
}

val File.dir: File get() = let { if (it.isFile) it.parentFile else it }

fun exeFile(): File =
  Runtime.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) } ?: File(".")

private object Runtime
