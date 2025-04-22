package kshare.feature

import daggerok.extensions.html.dom.h1
import kshare.*
import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import spark.Spark.*
import java.io.File
import java.io.OutputStream
import java.util.UUID
import javax.servlet.http.Part
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.notExists

fun enableFileDestination() {
    put()
    get()
    afterGet()
}

fun writeUpload(part: Part, username: String): Pair<UUID, String> {
    val uploadsFolder = Path("uploads/")
    if (uploadsFolder.notExists())
        uploadsFolder.createDirectory()

    val userFolder = uploadsFolder.resolve(username)
    if (userFolder.notExists())
        userFolder.createDirectory()

    val randomUUID = UUID.randomUUID()

    val uniqueSubfolder = userFolder.resolve(randomUUID.shorten())
    if (uniqueSubfolder.notExists())
        uniqueSubfolder.createDirectory()

    val newFile = uniqueSubfolder.resolve(part.submittedFileName).toFile()
    newFile.writeBytes(part.inputStream.readBytes())

    return randomUUID to newFile.toRelativeString(uploadsFolder.toFile())
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

                val username = ServerConfig.getUsername(req["Authorization"])

                val (uuid, fp) = writeUpload(filePart, username)

                transaction {
                    FileEntries.insert {
                        it[id] = EntityID(uuid, FileEntries)
                        it[type] = filePart.contentType
                        it[path] = fp
                        it[uploader] = username
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
            Path("uploads/")
                .resolve(fileEntry.filePath).toFile()
                .takeIf(File::exists)?.readBytes()
        } orHalt {
            loggedTransaction {
                "KShare".logger().info("Deleting upload ${fileEntry.id.value.shorten()} by ${fileEntry.uploader} with ${fileEntry.hits} hits...")
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
        "KShare".logger().info("$filePath is now at ${"hit".pluralize(newHits)}.")
    }
}