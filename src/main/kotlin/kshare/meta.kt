package kshare

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import daggerok.extensions.html.dom.HtmlBuilder
import daggerok.extensions.html.dom.html
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.Request
import spark.Response
import spark.Spark
import spark.utils.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.sql.SQLOutput
import java.util.*
import javax.servlet.MultipartConfigElement

fun Any.logger(): Logger = LoggerFactory.getLogger(this::class.java)
inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)

typealias static = JvmStatic

fun Transaction.sqlLogger(logger: StatementContext.(Transaction) -> Unit) {
    addLogger(object : SqlLogger {
        override fun log(context: StatementContext, transaction: Transaction) {
            context.logger(transaction)
        }
    })
}

fun<T> get(func: () -> T?) = ResponseHalter(func)


data class ResponseHalter<T>(private val func: () -> T?) {
    fun orHalt(httpStatus: Int, body: String): T = tryOrNull(func) ?: throw Spark.halt(httpStatus, body)
    fun orHalt(httpStatus: Int, func: HtmlBuilder.(String) -> Unit): T = orHalt(httpStatus, html(func = func))
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
        return@tryOrNull UUID(newBuffer.long, newBuffer.long)
    }
}

fun CharSequence.ensureAtEnd(str: String, ignoreCase: Boolean = false) = if (!endsWith(str, ignoreCase)) "$this$str" else this

fun OutputStream.copyFrom(inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE) = inputStream.copyTo(this, bufferSize)

fun InputStream.asString() = tryOrNull { IOUtils.toString(this) }

inline fun <V> tryOrNull(func: () -> V): V? = try {
    func()
} catch (t: Throwable) {
    null
}

fun <T> loggedTransaction(db: Database? = null, func: Transaction.() -> T): T {
    return transaction(db) {
        sqlLogger {
            logger<SQLOutput>().info(expandArgs(it))
        }
        func()
    }
}

fun gson(func: GsonBuilder.() -> Unit = {}): Gson = GsonBuilder().apply(func).create()


inline fun buildString(initialValue: String, builderAction: StringBuilder.() -> Unit): String
    = StringBuilder(initialValue).apply(builderAction).toString()


inline infix fun <reified T> Gson.fromJson(raw: String): T = this.fromJson(raw, T::class.java)

fun InputStream.blobify() = ExposedBlob(readBytes())