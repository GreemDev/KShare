package kshare

import daggerok.extensions.html.dom.h1
import org.eclipse.jetty.http.HttpStatus.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Redirect
import spark.Spark.*
import java.io.File
import java.io.OutputStream
import java.util.*

class KShare private constructor() {

    companion object {
        private lateinit var share: KShare
        fun get() = share

        @static
        fun main(args: Array<out String>) {

            if (tryOrNull { share } != null)
                throw IllegalStateException("Cannot reinitialize KShare.")

            ServerConfig.checks()

            Database.connect("jdbc:h2:${File(ServerConfig.databaseName()).absolutePath}")

            loggedTransaction {
                SchemaUtils.create(FileEntries)
            }

            share = KShare()
        }
    }


    init {
        port(ServerConfig.port())

        threadPool(25, 5, 5000)

        if (ServerConfig.allowAPI())
            enablePublicAPI()

        redirect.get("/", "https://github.com/GreemDev/KShare", Redirect.Status.MOVED_PERMANENTLY)

        logRequestStatus()
        filePut()
        fileGet()
        afterFileGet()
    }

    private fun afterFileGet() {
        after("/*") { req, resp ->
            if (resp.status() != 200) return@after
            val fileQuery = tryOrNull { req.splat().first().split('.') } // ensure file name matches name.extension

            if (fileQuery?.size != 2) return@after

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

    private fun logRequestStatus() = afterAfter { req, resp ->
        val location = buildString(ServerConfig.effectiveHost(req)) {
            appendIf(req.hasQueryString()) { "?${req.queryString()}" }
        }
        logger<KShare>().info("${req.requestMethod()} $location -> ${resp.status()}")
    }

    private fun fileGet() = get("/*") { req, resp ->
        val uuid = get {
            (tryOrNull {
                req.splat().first().split('.').first()
            } ?: req.splat().first())
                .toUUID()
        }.orHalt(IM_A_TEAPOT_418) {
            h1 {
                text("I'm a teapot that only likes valid UUIDs.")
            }
        }

        val fileEntry = get {
            transaction { FileEntry.findById(uuid) }
        }.orHalt(NOT_FOUND_404) {
            h1 {
                text("Specified file not found.")
            }
        }


        resp.status(OK_200)
        resp.type(fileEntry.type)
        fileEntry.data.bytes.inputStream().copyTo(resp.raw().outputStream)

        resp.raw().outputStream.also(OutputStream::flush)
    }

    private fun filePut() = put("/") { req, resp ->
        req.attributeMultipart("/temp")
        val prefs = req.raw().getHeader("Settings")
            ?.split(' ')
            ?.map {
                it.lowercase()
            } ?: listOf()

        if (ServerConfig.unauthorized(req.raw().getHeader("Authorization") ?: "")) {
            resp.halt(FORBIDDEN_403, "Action forbidden.")
        } else {
            try {
                val filePart = get { req.raw().getPart("file") }
                    .orHalt(BAD_REQUEST_400, "File form name configured in ShareX should be \"file\"; nothing else.")

                val uuid = UUID.randomUUID()

                transaction {
                    FileEntries.insert {
                        it[id] = uuid
                        it[type] = filePart.contentType
                        it[data] = filePart.inputStream.blobify()
                    }
                }

                buildString {
                    append(ServerConfig.effectiveHost(req).ensureAtEnd("/"))
                    append(if (prefs.contains("longuuid")) uuid else uuid.shorten())

                    appendIf(!prefs.contains("extensionless")) {
                        val ext = filePart.submittedFileName.split('.').last()
                        if (ext.isNotEmpty()) ".${ext}" else ""
                    }
                }

            } catch (t: Throwable) {
                resp.halt(INTERNAL_SERVER_ERROR_500, t.message ?: "Internal Server Error")
            }
        }
    }
}