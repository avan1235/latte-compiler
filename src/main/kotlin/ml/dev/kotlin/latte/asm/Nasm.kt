package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.util.*
import java.io.File
import java.nio.file.Files

fun nasm(assembly: File, libFile: File = DEFAULT_LIB_FILE): CompilationResult {
  val asmDir = assembly.dir.absolutePath
  val name = assembly.nameWithoutExtension
  val o = File(asmDir, "$name.o")
  val result = File(asmDir, name)
  listOf("nasm", "-f", "elf32", assembly.absolutePath, "-o", o.absolutePath).run()
  withLibFile(libFile) { lib ->
    listOf("gcc", "-m32", "-static", lib.absolutePath, o.absolutePath, "-o", result.absolutePath).run()
  }
  return CompilationResult(o, result)
}

data class CompilationResult(val oFile: File, val binFile: File)

private fun List<String>.run(): Unit =
  this().let { if (it != 0) throw CompileException(this.joinToString(" "), it) else Unit }

private val DEFAULT_LIB_FILE: File = File(exeFile().dir.absolutePath).resolve("lib").resolve("runtime.o")

private fun withLibFile(libFile: File, action: (File) -> Unit) {
  val useTemp = !libFile.exists()
  val lib = if (useTemp) createTempLibFromResources() else libFile
  try {
    if (useTemp) println("using runtime.o included in compiler binary as $libFile wasn't found")
    action(lib)
  } finally {
    if (useTemp) lib.delete()
  }
}

private fun createTempLibFromResources(): File {
  val file = Files.createTempFile(exeFile().dir.toPath(), "temp-runtime-", ".o").toFile()
  Nasm.javaClass.getResourceAsStream("runtime.o")?.let { file.writeBytes(it.readBytes()) }
    ?: throw LatteIllegalStateException("No runtime.o file in resources to use as backup".msg)
  return file
}

private object Nasm
