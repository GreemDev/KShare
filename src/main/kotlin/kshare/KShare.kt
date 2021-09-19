package kshare

import daggerok.extensions.html.dom.h1
import daggerok.extensions.html.dom.html
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Redirect
import spark.Spark.*
import spark.kotlin.after
import java.io.File
import java.util.*
import javax.servlet.MultipartConfigElement

class KShare private constructor() {

    companion object {
        private lateinit var share: KShare
        fun get() = share
        fun init() {

            if (tryOrNull { share } != null)
                throw IllegalStateException("Cannot reinitialize KShare.")

            ServerConfig.checks()

            Database.connect("jdbc:h2:${File(ServerConfig.databaseName()).absolutePath}")

            loggedTransaction {
                SchemaUtils.create(*allTables())
            }

            share = KShare()
        }
    }

    init {
        port(ServerConfig.port())

        threadPool(25, 5, 5000)

        if (ServerConfig.allowAPI())
            enablePublicAPI()

        after("/*") { req, resp ->
            if (resp.status() == 200) {
                val fileQuery = tryOrNull { req.splat().first().split('.') }

                if (fileQuery == null || fileQuery.size != 2) return@after

                val uuid = fileQuery.first().toUUID() ?: return@after
                transaction {
                    val fileEntry = FileEntry[uuid]
                    val newHitCount = fileEntry.hits.inc()
                    FileEntries.update {
                        it[hits] = newHitCount
                    }
                    logger<KShare>().info("$uuid is now at $newHitCount hit${if (newHitCount != 1) "s" else ""}.")
                }
            }
        }

        redirect.get("/", "https://github.com/GreemDev/KShare", Redirect.Status.MOVED_PERMANENTLY)

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
            logger<KShare>().info("${req.requestMethod()} $location -> ${resp.status()}")
        }

        get("/*") { req, resp ->
            val fileQuery = req.splat().first().split('.')

            if (fileQuery.size != 2)
                halt(418, html {
                    h1 {
                        text("I'm a teapot that only likes properly-formed URLs.")
                    }
                })

            val uuid = fileQuery.first().toUUID()
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

        after("/", "put") {
            response.header("Content-Encoding", "gzip")
        }

        put("/") { req, resp ->
            req.attribute("org.eclipse.jetty.multipartConfig", MultipartConfigElement("/temp"))
            val prefs = req.raw().getPart("settings")?.inputStream?.asString()?.split(' ') ?: listOf()

            if (!ServerConfig.authorized(req.raw().getPart("key")?.inputStream?.asString())) {
                resp.halt(403, "Action forbidden.")
            } else {
                try {
                    val filePart = req.raw().getPart("file")
                    if (filePart == null) {
                        resp.halt(400, "File form name configured in ShareX should be \"file\"; nothing else.")
                    }

                    val uuid = UUID.randomUUID()

                    loggedTransaction {
                        FileEntries.insert {
                            it[id] = uuid
                            it[type] = filePart.contentType
                            it[data] = filePart.inputStream.blobify()
                        }
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
                        append(uuid.shorten())

                        val ext = filePart.submittedFileName.split('.').last()

                        if (ext.isNotEmpty())
                            append(".${ext}")
                    }

                } catch (t: Throwable) {
                    resp.halt(500, t.message ?: "Internal Server Error")
                }
            }
        }
    }

}