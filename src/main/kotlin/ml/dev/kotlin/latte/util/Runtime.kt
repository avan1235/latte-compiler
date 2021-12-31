package ml.dev.kotlin.latte.util

import java.io.File

operator fun String.invoke(workingDir: File = exeFile()): Int = try {
  ProcessBuilder(*split(" ").toTypedArray())
    .directory(workingDir.dir)
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
    .waitFor()
} catch (e: Throwable) {
  eprintln(e.message)
  -1
}

val File.dir: File get() = let { if (it.isFile) it.parentFile else it }

fun exeFile(): File =
  Runtime.javaClass.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) } ?: File(".")

private object Runtime
