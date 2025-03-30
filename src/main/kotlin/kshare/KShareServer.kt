package kshare

import org.jetbrains.exposed.sql.Database
import spark.*
import spark.Spark.*
import kshare.feature.*
import spark.staticfiles.StaticFilesFolder
import java.io.File


object KShareServer {

    private var isInitialized = false

    @static
    fun main(args: Array<out String>) {
        if (isInitialized)
            throw IllegalStateException("Cannot reinitialize KShare.")

        ServerConfig.checks()

        Database.connect("jdbc:h2:${File(ServerConfig.databaseRootName).absolutePath}")

        createTables()

        startServer()

        isInitialized = true
    }

    fun startServer() {
        port(ServerConfig.port)

        threadPool(25, 5, 5000)

        if (ServerConfig.apiEnabled)
            enablePublicAPI()

        redirect.get("/", "https://github.com/GreemDev/KShare", Redirect.Status.MOVED_PERMANENTLY)

        with(File("staticFiles/")) {
            if (!exists() || !isDirectory) {
                with(logger<StaticFilesFolder>()) {
                    warn("Skipping initialization of the static file server.")
                    warn("To enable it, create a directory named 'staticFiles' in the same directory as this application.")
                }
            } else enableStaticFileServer()
        }

        enableFileDestination()
        enableRequestLogger()
    }
}