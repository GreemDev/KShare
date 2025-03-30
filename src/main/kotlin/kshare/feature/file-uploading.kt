package kshare.feature

import daggerok.extensions.html.dom.h1
import kshare.*
import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Spark.*
import java.io.OutputStream

fun enableFileDestination() {
    put()
    get()
    afterGet()
}

// file uploading
private fun put() {
    put("/") { req, resp ->
        req.attributeMultipart("/temp")
        val prefs = req.raw().getHeader("Settings")
            ?.split(' ')
            ?.map {
                it.lowercase()
            } ?: listOf()


        if (ServerConfig.unauthorized(req["Authorization"])) {
            resp.halt(HttpStatus.FORBIDDEN_403, "Action forbidden.")
        } else {
            runCatching {
                val filePart = get { req.raw().getPart("file") }
                    .orHalt(HttpStatus.BAD_REQUEST_400, "File form name configured in ShareX should be \"file\"; nothing else.")

                val uuid = transaction {
                    FileEntries.insert {
                        it[type] = filePart.contentType
                        it[data] = filePart.inputStream.blobify()
                    } get FileEntries.id
                }.value

                buildString {
                    append(ServerConfig.effectiveHost(req).ensureAtEnd("/"))
                    append(if (prefs.contains("longuuid")) uuid else uuid.shorten())

                    appendIf(!prefs.contains("extensionless")) {
                        val ext = filePart.submittedFileName.split('.').last()
                        if (ext.isNotEmpty()) ".${ext}" else ""
                    }
                }
            }.getOrElse {
                resp.halt(HttpStatus.INTERNAL_SERVER_ERROR_500, it.message ?: "Internal Server Error")
            }
        }
    }
}

// viewing
private fun get() =
    get("/*") { req, resp ->
        val uuid = get {
            (tryOrNull {
                req.splat().first().split('.').first()
            } ?: req.splat().first())
                .toUUID()
        } orHalt {
            h1 {
                text("I'm a teapot that only likes valid UUIDs.")
            }
            HttpStatus.IM_A_TEAPOT_418
        }

        val fileEntry = get {
            transaction { FileEntry.findById(uuid) }
        } orHalt  {
            h1 {
                text("Specified file not found.")
            }
            HttpStatus.NOT_FOUND_404
        }

        resp.status(HttpStatus.OK_200)
        resp.type(fileEntry.type)

        resp.raw().outputStream
            .copyFrom(fileEntry.data.bytes.inputStream())
            .also(OutputStream::flush)
    }


// file hit counter
private fun afterGet() {
    after("/*") { req, _ ->
        val fileQuery = tryOrNull {
            req.splat().first().split('.')
        }.takeIf { it?.size == 2 } ?: return@after

        val uuid = fileQuery.first().toUUID() ?: return@after
        val newHits = transaction {
            val fileEntry = FileEntry[uuid]

            fileEntry.hits.inc().also { count ->
                FileEntries.update {
                    it[hits] = count
                }
            }
        }
        "KShare".logger().info("$uuid is now at ${"hit".pluralize(newHits)}.")
    }
}