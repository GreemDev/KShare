package kshare

import com.google.gson.Gson
import spark.Request
import spark.Spark
import java.io.File
import kotlin.system.exitProcess


@Suppress("ArrayInDataClass")
data class ServerConfig(
    private val validAuthKeys: Array<String> = arrayOf("1+ keys here"), // Authorization key for uploading files.
    private val host: String = "https://your-url.here",                 // Your KShare domain, because this app only ever gives you localhost.
    private val enableApi: Boolean = true,                              // Whether to enable the bare-bones statistics API or not.
    private val port: Int = 6969, // ha, funny number                   // The port to host the server on.
    private val production: Boolean = false,                            // Whether to use the `host` URL for uploading files or just the localhost URL.
    private val dataFileName: String = "kshare"                         // The name for your database file.
) {

    companion object {

        private fun gson(): Gson = gson { setPrettyPrinting() }

        fun authorized(key: String?): Boolean = auth().any { it == key }
        fun unauthorized(key: String?): Boolean = !authorized(key)

        fun effectiveHost(request: Request): String = buildString(request.url()) {
            if (isProd())
                replace(0, "http://localhost:${port()}".length.inc(), host())
        }

        fun auth() = get()!!.validAuthKeys
        fun host() = get()!!.host
        fun allowAPI() = get()!!.enableApi
        fun port() = get()!!.port
        fun isProd() = get()!!.production
        fun databaseName() = get()!!.dataFileName

        fun checks() {
            if (!file().exists()) {
                write()
                logger().warn("Please fill in the config.json file and restart!")
                exitProcess(69)
            }
            if (file().readText().isEmpty())
                write()

            if (get()!!.validAuthKeys.any { it == "1+ keys here" } or get()!!.validAuthKeys.isEmpty()) {
                logger().warn("You need to provide an authKey in order to start the server.")
                exitProcess(420)
            }
        }
        fun file() = File("config.json").apply {
            if (exists()) {
                setReadable(true)
                setWritable(true)
            } else {
                createNewFile()
            }
        }

        fun write(config: ServerConfig = ServerConfig()) {
            file().writeText(gson().toJson(config))
        }

        fun get(): ServerConfig? =
            try {
                gson().fromJson<ServerConfig>(file().readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
    }
}