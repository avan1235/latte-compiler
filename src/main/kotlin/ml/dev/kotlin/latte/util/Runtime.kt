package ml.dev.kotlin.latte.util

import java.io.File
import java.lang.ProcessBuilder

operator fun String.invoke(
  inputFile: File? = null,
  outFile: File? = null,
  errFile: File? = null,
  workingDir: File = exeFile()
): Int = listOf(this).invoke(inputFile, outFile, errFile, workingDir)

operator fun List<String>.invoke(
  inputFile: File? = null,
  outFile: File? = null,
  errFile: File? = null,
  workingDir: File = exeFile()
): Int {
  return try {
    val pb = ProcessBuilder(this).directory(workingDir.dir)
    if (inputFile != null) pb.redirectInput(inputFile) else pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
    if (outFile != null) pb.redirectOutput(outFile) else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    if (errFile != null) pb.redirectError(errFile) else pb.redirectError(ProcessBuilder.Redirect.INHERIT)
    pb.start().waitFor()
  } catch (e: Throwable) {
    eprintln(e.message)
    -1
  }
}

fun Int.zeroCode(): Unit =
  if (this == 0) Unit else throw LatteRuntimeException("Process returned not zero code $this".msg)

val File.dir: File get() = let { if (it.isFile) it.absoluteFile.parentFile else it }

fun File.withExtension(ext: String, data: String? = null): File =
  dir.resolve("${nameWithoutExtension}$ext").apply { data?.let { writeText(it) } }

fun exeFile(): File =
  Runtime.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) } ?: File(".")

private object Runtime
