package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

abstract class LatteException(val userMessage: LocalizedMessage) : CancellationException("$userMessage")

sealed class FrontendException(userMessage: LocalizedMessage) : LatteException(userMessage)
class ParseException(userMessage: LocalizedMessage) : FrontendException(userMessage)
class TypeCheckException(userMessage: LocalizedMessage) : FrontendException(userMessage)

class IRException(userMessage: LocalizedMessage) : LatteException(userMessage)

class AsmBuildException(userMessage: LocalizedMessage) : LatteException(userMessage)
class CompileException(code: Int) : LatteException("Compilation process returned non zero code $code".msg)
class GraphException(userMessage: LocalizedMessage) : LatteException(userMessage)
