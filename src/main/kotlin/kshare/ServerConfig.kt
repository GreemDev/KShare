package kshare

import com.google.gson.Gson
import java.io.File
import kotlin.system.exitProcess


class ServerConfig {

    private val authKey = "your key here"
    private val host = "your-url.here"
    private val enableApi = true
    private val port = 8082
    private val production = false
    private val dataFileName = "share"

    companion object {

        fun authorized(key: String?): Boolean = auth() == key

        fun auth() = get()!!.authKey
        fun host() = get()!!.host
        fun allowAPI() = get()!!.enableApi
        fun port() = get()!!.port
        fun isProd() = get()!!.production
        fun databaseName() = get()!!.dataFileName

        fun checks() {
            if (!file().exists()) {
                write()
                logger().warn("Please fill in the config.json file and restart!")
                exitProcess(-1)
            }
            if (file().readText().isEmpty())
                write()

            if ((get()?.authKey == "your key here") or (get()?.authKey?.isEmpty() != false)) {
                logger().warn("You need to provide an authKey in order to start the server.")
                exitProcess(-1)
            }
        }

        private val gson: Gson = gson { setPrettyPrinting() }
        fun file() = File("config.json").apply {
            if (exists()) {
                setReadable(true)
                setWritable(true)
            } else {
                this.createNewFile()
            }
        }

        fun write(config: ServerConfig = ServerConfig()) {
            file().writeText(gson.toJson(config))
        }

        fun get(): ServerConfig? =
            try {
                gson.fromJson<ServerConfig>(file().readText())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
    }
}