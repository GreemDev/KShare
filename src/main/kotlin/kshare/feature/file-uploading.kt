package kshare.feature

import daggerok.extensions.html.dom.h1
import kshare.*
import kshare.util.appendIf
import kshare.util.attributeMultipart
import kshare.util.ensureAtEnd
import kshare.util.get
import kshare.util.loggedTransaction
import kshare.util.pluralize
import kshare.util.shorten
import kshare.util.toUUID
import kshare.util.tryOrNull
import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Spark.*
import java.io.OutputStream
import kotlin.io.path.extension

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
                val uploadedFile = FileEntries.writeUpload(req)

                buildString {
                    append(ServerConfig.effectiveHost(req).ensureAtEnd("/"))
                    append(if (prefs.contains("longuuid")) uploadedFile.id.value else uploadedFile.id.value.shorten())

                    appendIf(!prefs.contains("extensionless")) {
                        val ext = uploadedFile.uploadedFileName().extension
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

        val fileBytes = get {
            fileEntry.tryReadFileBytes()
        } orHalt {
            loggedTransaction {
                kshareLogger.info("Deleting upload ${fileEntry.id.value.shorten()} by ${fileEntry.uploader} with ${fileEntry.hits} hits...")
                FileEntries.deleteWhere { path eq fileEntry.filePath }
            }
            h1 {
                text("Specified file not found.")
            }
            HttpStatus.NOT_FOUND_404
        }

        resp.status(HttpStatus.OK_200)
        resp.type(fileEntry.type)

        resp.raw().outputStream
            .apply { write(fileBytes) }
            .also(OutputStream::flush)
    }


// file hit counter
private fun afterGet() {
    after("/*") { req, _ ->
        val fileQuery = tryOrNull {
            req.splat().first().split('.')
        }.takeIf { it?.size == 2 } ?: return@after

        val uuid = fileQuery.first().toUUID() ?: return@after
        val (newHits, filePath) = transaction {
            val fileEntry = FileEntry[uuid]

            val newHits = fileEntry.hits.inc().also { count ->
                FileEntries.update {
                    it[hits] = count
                }
            }

            newHits to fileEntry.filePath
        }
        kshareLogger.info("$filePath is now at ${"hit".pluralize(newHits)}.")
    }
}