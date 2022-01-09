package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

abstract class LatteException(val userMessage: LocalizedMessage) : CancellationException("$userMessage")

sealed class FrontendException(userMessage: LocalizedMessage) : LatteException(userMessage)
class ParseException(userMessage: LocalizedMessage) : FrontendException(userMessage)
class TypeCheckException(userMessage: LocalizedMessage) : FrontendException(userMessage)
class ClassHierarchyException(userMessage: LocalizedMessage) : FrontendException(userMessage)

class IRException(userMessage: LocalizedMessage) : LatteException(userMessage)

class AsmBuildException(userMessage: LocalizedMessage) : LatteException(userMessage)
class MemoryAllocationException(userMessage: LocalizedMessage) : LatteException(userMessage)
class CompileException(cmd: String, code: Int) :
  LatteException("Compilation with $cmd returned non zero code $code".msg)

class GraphException(userMessage: LocalizedMessage) : LatteException(userMessage)
class LatteIllegalStateException(userMessage: LocalizedMessage) : LatteException(userMessage)
class LatteRuntimeException(userMessage: LocalizedMessage) : LatteException(userMessage)
