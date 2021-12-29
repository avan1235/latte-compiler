package ml.dev.kotlin.latte.util

import java.util.concurrent.CancellationException

abstract class LatteException(val userMessage: ExceptionLocalizedMessage) : CancellationException("$userMessage")

sealed class FrontendException(userMessage: ExceptionLocalizedMessage) : LatteException(userMessage)
class ParseException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)
class TypeCheckException(userMessage: ExceptionLocalizedMessage) : FrontendException(userMessage)

class IRException(userMessage: ExceptionLocalizedMessage) : LatteException(userMessage)

class CompileException(code: Int) : LatteException("Compilation process returned non zero code $code".msg)
