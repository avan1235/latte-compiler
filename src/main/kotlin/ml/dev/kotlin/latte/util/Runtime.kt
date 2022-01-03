package ml.dev.kotlin.latte.util

import java.io.File
import java.lang.ProcessBuilder

operator fun String.invoke(
  inputFile: File? = null,
  outFile: File? = null,
  errFile: File? = null,
  workingDir: File = exeFile()
): Int {
  return try {
    val pb = ProcessBuilder(*split(" ").toTypedArray()).directory(workingDir.dir)
    if (inputFile != null) pb.redirectInput(inputFile) else pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
    if (outFile != null) pb.redirectOutput(outFile) else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    if (errFile != null) pb.redirectError(errFile) else pb.redirectError(ProcessBuilder.Redirect.INHERIT)
    pb.start().waitFor()
  } catch (e: Throwable) {
    eprintln(e.message)
    -1
  }
}

val File.dir: File get() = let { if (it.isFile) it.parentFile else it }

fun exeFile(): File =
  Runtime.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) } ?: File(".")

private object Runtime
