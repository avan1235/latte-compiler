package ml.dev.kotlin.latte.util

import java.io.File

operator fun String.invoke(workingDir: File = exeFile()) = ProcessBuilder(*split(" ").toTypedArray())
  .directory(workingDir.dir)
  .redirectOutput(ProcessBuilder.Redirect.INHERIT)
  .redirectError(ProcessBuilder.Redirect.INHERIT)
  .start()
  .waitFor()

val File.dir: File get() = let { if (it.isFile) it.parentFile else it }

fun exeFile(): File =
  Runtime.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) } ?: File(".")

private object Runtime
