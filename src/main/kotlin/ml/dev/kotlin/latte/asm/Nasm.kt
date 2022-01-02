package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.util.CompileException
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.exeFile
import ml.dev.kotlin.latte.util.invoke
import java.io.File

fun nasm(assembly: File, libFile: File = DEFAULT_LIB_FILE) {
  val asmDir = assembly.dir.absolutePath
  val name = assembly.nameWithoutExtension
  val o = File(asmDir, "$name.o")
  val result = File(asmDir, name)
  "nasm -f elf32 ${assembly.absolutePath} -o ${o.absolutePath}".checkCode()
  "gcc -m32 ${libFile.absolutePath} ${o.absolutePath} -o ${result.absolutePath}".checkCode()
}

private fun String.checkCode(): Unit = this().let { if (it != 0) throw CompileException(this, it) else Unit }

private val DEFAULT_LIB_FILE: File = File(exeFile().dir.absolutePath).resolve("lib").resolve("runtime.o")
