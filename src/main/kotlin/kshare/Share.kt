package kshare

import daggerok.extensions.html.dom.h1
import daggerok.extensions.html.dom.html
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Spark.*
import java.io.File
import java.util.*
import javax.servlet.MultipartConfigElement

class Share private constructor() {

    companion object {
        private lateinit var share: Share
        fun get() = share
        fun init() {

            if (tryOrNull { share } != null)
                throw IllegalStateException("Cannot reinitialize KShare.")

            ServerConfig.checks()

            Database.connect("jdbc:h2:${File(ServerConfig.databaseName()).absolutePath}")

            loggedTransaction {
                SchemaUtils.create(*allTables())
            }

            Share()
        }
    }

    init {
        port(ServerConfig.port())

        if (ServerConfig.allowAPI())
            enablePublicAPI()

        Thread.sleep(200)

        after("/:id/*") { req, resp ->
            if (resp.status() == 200) {
                val uuid = tryOrNull { UUID.fromString(req.params(":id")) } ?: return@after
                transaction {
                    val fileEntry = FileEntry[uuid]
                    FileEntries.update {
                        it[hits] = fileEntry.hits.inc()
                    }
                    logger<Share>().info("${req.params(":id")} is now at ${fileEntry.hits.inc()} hits.")
                }
            }
        }

        get("/") { _, resp ->
            resp.redirect("https://github.com/GreemDev/KShare", 301)
        }

        afterAfter { req, resp ->
            val location = buildString {
                if (ServerConfig.isProd())
                    append(req.url().replace("http://localhost:${ServerConfig.port()}", ServerConfig.host()))
                else
                    append(req.url())

                append(
                    if (tryOrNull { req.queryString() } != null)
                        "?${req.queryString()}"
                    else
                        ""
                )
            }
            logger<Share>().info("${req.requestMethod()} $location -> ${resp.status()}")
        }

        get("/:id/*") { req, resp ->
            val uuid = tryOrNull { UUID.fromString(req.params(":id")) }
            if (uuid == null) {
                resp.halt(418, html {
                    h1 {
                        text("I'm a teapot that only likes valid UUIDs.")
                    }
                })
            }

            val fileEntry = transaction { FileEntry.findById(uuid!!) }
            if (fileEntry != null) {
                resp.status(200)
                resp.type(fileEntry.type)
                fileEntry.data.bytes.inputStream().copyTo(resp.raw().outputStream)
                resp.raw().outputStream.flush()
                resp.raw().outputStream.close()

                resp.raw().outputStream

            } else {
                resp.halt(404, html {
                    h1 {
                        text("Specified file not found.")
                    }
                })
            }
        }

        put("/") { req, resp ->
            req.attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement("/temp"))
            val providedKey = req.raw().getPart("key")?.inputStream?.asString()
            val urlPrefs = req.raw().getPart("settings")?.inputStream?.asString()?.split(' ') ?: listOf()

            if (!ServerConfig.authorized(providedKey)) {
                resp.halt(403, "Action forbidden.")
            } else {
                try {
                    val filePart = req.raw().getPart("file")
                    if (filePart == null) {
                        resp.halt(400, "File form name configured in ShareX should be \"file\"; nothing else.")
                    }

                    val uuid = UUID.randomUUID()

                    val extension = loggedTransaction {
                        FileEntries.insert {
                            it[id] = uuid
                            it[hits] = 0
                            it[extension] = tryOrNull { filePart.submittedFileName.split('.').last() } ?: ""
                            it[type] = filePart.contentType
                            it[data] = filePart.inputStream.blobify()
                        } get FileEntries.extension
                    }

                    buildString {
                        append((
                                if (ServerConfig.isProd())
                                    ServerConfig.host()
                                else
                                    req.url()
                                )
                            .ensureAtEnd("/")
                        )
                        append(uuid)

                        if (urlPrefs.contains("namedFile"))
                            append("/${filePart.submittedFileName}")
                        else if (extension.isNotEmpty())
                            append("/file.${extension}")
                    }

                } catch (t: Throwable) {
                    resp.halt(500, t.message ?: "Internal Server Error")
                }
            }
        }

        share = this
    }

}