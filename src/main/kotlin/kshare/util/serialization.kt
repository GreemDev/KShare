package kshare.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun json(pretty: Boolean) = json(pretty) { this }
fun<R> json(pretty: Boolean, block: Json.() -> R) = (if (pretty) prettyJson else Json).run(block)

val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

inline fun <reified T> parseJsonString(json: String, pretty: Boolean = true): T =
    json(pretty) { decodeFromString(json) }

inline fun<reified T> formatJsonString(src: T, pretty: Boolean = true): String =
    json(pretty) { encodeToString(src) }