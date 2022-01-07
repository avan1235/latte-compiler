package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.util.CompileException
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.exeFile
import ml.dev.kotlin.latte.util.invoke
import java.io.File

fun nasm(assembly: File, libFile: File = DEFAULT_LIB_FILE): CompilationResult {
  val asmDir = assembly.dir.absolutePath
  val name = assembly.nameWithoutExtension
  val o = File(asmDir, "$name.o")
  val result = File(asmDir, name)
  listOf("nasm", "-f", "elf32", assembly.absolutePath, "-o", o.absolutePath).run()
  listOf("gcc", "-m32", "-static", libFile.absolutePath, o.absolutePath, "-o", result.absolutePath).run()
  return CompilationResult(o, result)
}

data class CompilationResult(val oFile: File, val binFile: File)

private fun List<String>.run(): Unit =
  this().let { if (it != 0) throw CompileException(this.joinToString(" "), it) else Unit }

private val DEFAULT_LIB_FILE: File = File(exeFile().dir.absolutePath).resolve("lib").resolve("runtime.o")
