package kshare

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import daggerok.extensions.html.dom.h2
import daggerok.extensions.html.dom.html
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spark.utils.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.sql.SQLOutput

fun Any.logger(): Logger = LoggerFactory.getLogger(this::class.java)
inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)

typealias static = JvmStatic

internal object ServerSqlLogger : SqlLogger {
    override fun log(context: StatementContext, transaction: Transaction) {
        logger<SQLOutput>().info(context.expandArgs(transaction))
    }
}

fun CharSequence.ensureAtEnd(str: String, ignoreCase: Boolean = false) = if (!endsWith(str, ignoreCase)) "$this$str" else this

fun OutputStream.copyFrom(inputStream: InputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE) = inputStream.copyTo(this, bufferSize)

fun InputStream.asString() = tryOrNull { IOUtils.toString(this) }

fun <V> tryOrNull(func: () -> V): V? = try {
    func()
} catch (t: Throwable) {
    null
}

fun <T> loggedTransaction(db: Database? = null, func: Transaction.() -> T): T {
    return transaction(db) {
        addLogger(ServerSqlLogger)
        func()
    }
}

object Main {

    @static fun main(args: Array<out String>) = Share.init()
}

fun gson(func: GsonBuilder.() -> Unit = {}): Gson = GsonBuilder().apply(func).create()


inline fun buildString(initialValue: String, builderAction: StringBuilder.() -> Unit): String
    = StringBuilder(initialValue).apply(builderAction).toString()


inline infix fun <reified T> Gson.fromJson(raw: String): T = this.fromJson(raw, T::class.java)

fun InputStream.blobify() = ExposedBlob(readBytes())