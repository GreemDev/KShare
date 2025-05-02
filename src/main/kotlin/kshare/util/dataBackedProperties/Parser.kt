package kshare.util.dataBackedProperties

abstract class Parser<in TInput, TOutput> {
    protected abstract fun parse(value: TInput): TOutput

    infix fun unsafeParse(value: TInput): TOutput = parse(value)
    infix fun tryParse(value: TInput): Result<TOutput> = runCatching { parse(value) }

    operator fun invoke(value: TInput) = tryParse(value)

    companion object {
        inline infix fun <TIn, TOut> byCustom(crossinline parser: (TIn) -> TOut) = object : Parser<TIn, TOut>() {
            override fun parse(value: TIn): TOut = parser(value)
        }
    }
}

inline fun <TIn, TOut> newCustomParser(crossinline parseBlock: (TIn) -> TOut) = Parser byCustom parseBlock

class ParsingError(override val message: String?, override val cause: Throwable? = null) : Throwable(message, cause) {
    companion object {

        class ExceptionScope(val message: String, val cause: Throwable? = null) {
            fun badInput() = ParsingError("Bad input: $message", cause)
            fun format() = ParsingError("Invalid format: $message", cause)
            fun emptyParse() = ParsingError("No recognizable $message found in the input value", cause)
        }

        fun throwing(message: String, cause: Throwable? = null, block: ExceptionScope.() -> Throwable): Nothing {
            throw ExceptionScope(message, cause).block()
        }
    }
}