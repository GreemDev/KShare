package kshare

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spark.Request
import java.io.File
import kotlin.properties.Delegates
import kotlin.system.exitProcess

val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val defaultBlacklistedStaticFiles = arrayOf(".gitkeep", ".gitignore")

@Suppress("ArrayInDataClass")
@Serializable
data class ServerConfig(
    val validAuthKeys: Map<String, String> = mapOf("default" to "1+ keys here"),   // usernames -> authorization keys for uploading files.
    val host: String = "https://your-url.here",                                    // Your KShare domain, because this app only ever gives you localhost.
    val enableApi: Boolean = true,                                                 // Whether to enable the bare-bones statistics API or not.
    val port: Int = 6969, // ha, funny number                                      // The port to host the server on.
    val production: Boolean = false,                                               // Whether to use the `host` URL for uploading files or just the localhost URL.
    val allowStaticFileDiscovery: Boolean = false,                                 // Whether to allow anyone to see every file in staticFiles/ via using the `/fs` base URL with no path provided
    val blacklistedStaticFiles: Array<String> = defaultBlacklistedStaticFiles,     // Whether to allow anyone to see every file in staticFiles/ via using the `/fs` base URL with no UUID
    val dataFileName: String = "kshare"                                            // The name for your database file.
) {

    companion object {

        fun authorized(key: String?): Boolean = key in authKeys.values
        fun unauthorized(key: String?): Boolean = key !in authKeys.values

        fun getUsername(key: String): String = authKeys.entries.firstOrNull { it.value == key }?.key ?: error("Could not find a username for that key.")

        fun getValidUsernames() = authKeys.keys

        fun effectiveHost(request: Request, modifier: String.() -> String = { this }): String =
            modifier(buildString(request.url()) {
                if (isProduction)
                    replace(0, "http://localhost:${port}".length.inc(), host)
            })

        val authKeys by Delegates.invoking { readConfig()!!.validAuthKeys }
        val host by Delegates.invoking { readConfig()!!.host }
        val apiEnabled by Delegates.invoking { readConfig()!!.enableApi }
        val port by Delegates.invoking { readConfig()!!.port }
        val isProduction by Delegates.invoking { readConfig()!!.production }
        val shouldAllowStaticFileDiscovery by Delegates.invoking { tryOrNull { readConfig()!!.allowStaticFileDiscovery } ?: false }
        val blacklistedStaticFiles by Delegates.invoking { tryOrNull { readConfig()!!.blacklistedStaticFiles } ?: defaultBlacklistedStaticFiles }
        val databaseRootName by Delegates.invoking { readConfig()!!.dataFileName }

        fun checks() {
            if (!file.exists()) {
                write()
                logger().warn("Please fill in the config.json file and restart!")
                exitProcess(69)
            }
            if (file.readText().isEmpty())
                write()

            readConfig()!!.run {
                if (validAuthKeys.any { it.key == "default" && it.value == "1+ keys here" } or validAuthKeys.isEmpty()) {
                    logger().warn("You need to provide an authKey in order to start the server.")
                    exitProcess(420)
                }
            }
        }

        val file by Delegates.invoking {
            File("config.json").apply {
                if (exists()) {
                    setReadable(true)
                    setWritable(true)
                }
            }
        }

        fun write(config: ServerConfig = ServerConfig()) {
            file.writeText(json.encodeToString(config))
        }

        fun readConfig(): ServerConfig? =
            runCatching {
                json.decodeFromString<ServerConfig>(file.readText())
            }.getOrElse {
                it.printStackTrace()
                null
            }
    }
}