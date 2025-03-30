package kshare

import daggerok.extensions.html.dom.HtmlBuilder
import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.Request
import spark.Spark
import spark.utils.IOUtils
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.sql.SQLOutput
import java.util.*
import javax.servlet.MultipartConfigElement
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

fun Any.logger(): Logger = LoggerFactory.getLogger(this::class.java)

fun String.logger(): Logger = LoggerFactory.getLogger(this)
fun KClass<*>.logger(): Logger = LoggerFactory.getLogger(java)
fun Class<*>.logger() = kotlin.logger()
inline fun <reified T> logger(): Logger = T::class.java.logger()

typealias static = JvmStatic

fun Transaction.addSqlLogger(logger: StatementContext.(Transaction) -> Unit) =
    addLogger(object : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            context.logger(transaction)
        }
    })

fun<T> get(func: () -> T?) = ResponseHalter(func)

data class ResponseHalter<T>(private val func: () -> T?) {
    fun orHalt(httpStatus: Int, body: String): T = tryOrNull(func) ?: halt(httpStatus, body)
    infix fun orHalt(builder: HtmlBuilder.(Throwable?) -> Int?): T = runCatching {
        func()!!
    }.getOrElse { halt(it, builder) }
}

fun halt(status: Int): Nothing = haltInternal(status, null)
fun halt(body: String): Nothing = haltInternal(null, body)
fun halt(status: Int, body: String): Nothing = haltInternal(status, body)
fun halt(error: Throwable? = null, builder: HtmlBuilder.(Throwable?) -> Int?): Nothing {
    var httpStatus: Int?
    val html = HtmlBuilder().apply {
        httpStatus = builder(error)
    }
    halt(httpStatus ?: HttpStatus.OK_200, html.innerHTML)
}

private fun haltInternal(status: Int? = null, body: String? = null): Nothing {
    if (status == null && body == null)
        error("Either a status, body, or both is required.")
    if (status == null && body != null)
        Spark.halt(body)
    else if (status != null && body == null)
        Spark.halt(status)
    else
        Spark.halt(status!!, body)

    error("Unreachable") //halt is a java method and as such throws exceptions without using the kotlin-specific Nothing type.
}

fun Request.hasQueryString() = tryOrNull { queryString() } != null

fun Request.attributeMultipart(location: String)
        = attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement(location))

fun UUID.shorten(): String {
    val buffer = ByteBuffer.wrap(ByteArray(16)).apply {
        putLong(mostSignificantBits)
        putLong(leastSignificantBits)
    }

    return Base64.getUrlEncoder()
        .encodeToString(buffer.array())
        .trimEnd { it == '=' }

}

val File.areAnyParentsHidden: Boolean
    get() {
        var parent: File? = parentFile

        while (parent != null) {
            if (parent.isHidden) return true
            parent = parent.parentFile
        }

        return false
    }

fun StringBuilder.appendIf(condition: Boolean, func: () -> String): StringBuilder {
    if (condition)
        append(func())

    return this
}

fun String.toUUID(): UUID? {
    return tryOrNull {
        UUID.fromString(this)
    } ?: tryOrNull {
        val newBuffer = ByteBuffer.wrap(Base64.getUrlDecoder().decode("$this=="))
        UUID(newBuffer.long, newBuffer.long)
    }
}

operator fun<T> T.invoke(block: T.() -> Unit) = apply(block)

operator fun Request.get(header: String): String = headers(header)

fun CharSequence.ensureAtEnd(str: String, ignoreCase: Boolean = false) = if (!endsWith(str, ignoreCase)) "$this$str" else this

fun<T : OutputStream> T.copyFrom(inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): T {
    inputStream.copyTo(this, bufferSize)
    return this
}

fun InputStream.asString() = tryOrNull { IOUtils.toString(this) }

inline fun <V> tryOrNull(func: () -> V): V? = try {
    func()
} catch (t: Throwable) {
    null
}

fun <T> loggedTransaction(db: Database? = null, func: Transaction.() -> T) =
    transaction(db) {
        addSqlLogger {
            logger<SQLOutput>().info(expandArgs(it))
        }
        func()
    }

inline fun buildString(initialValue: String, builderAction: StringBuilder.() -> Unit): String
    = StringBuilder(initialValue).apply(builderAction).toString()

fun InputStream.blobify() = ExposedBlob(readBytes())

@Suppress("UnusedReceiverParameter")
fun<T> Delegates.invoking(func: () -> T) = ReadOnlyProperty<Any?, T> { _, _ -> func() }

@JvmOverloads
fun String.pluralize(quantity: Number, useES: Boolean = false, prefixQuantity: Boolean = true) =
    if (quantity != 1) buildString {
        if (prefixQuantity) append("$quantity ")
        append(this@pluralize)
        if (useES) append('e')
        append('s')
    } else {
        if (prefixQuantity) "$quantity $this"
        else this
    }