package kshare.feature

import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.sql.transactions.transaction
import kshare.*
import kshare.util.get
import kshare.util.toUUID
import spark.*
import spark.Spark.*
import java.io.File

enum class FileSize {
    Gigabytes,
    Megabytes,
    Kilobytes,
    Bytes;

    companion object {
        fun parse(fileSizeName: String) = when (fileSizeName.lowercase()) {
            "gigabytes", "giga", "gb", "g" -> Gigabytes
            "megabytes", "mega", "mb", "m" -> Megabytes
            "kilobytes", "kilo", "kb", "k" -> Kilobytes
            "bytes", "b" -> Bytes
            else -> null
        }
    }
}

fun Number.asFileSize(fileSize: FileSize = FileSize.Megabytes): String {

    fun Number.tier(): Number = toLong() / 1024

    return when (fileSize) {
        FileSize.Bytes -> "$this B"
        FileSize.Kilobytes -> "${tier()} KB"
        FileSize.Megabytes -> "${tier().tier()} MB"
        FileSize.Gigabytes -> "${tier().tier().tier()} GB"
    }
}


fun Response.halt(status: Int, body: String): HaltException = Spark.halt(status, body).also {
    status(status)
}


fun hitsById(req: Request, resp: Response): Any {
    val uuid = get { req.params(":id").toUUID() }
        .orHalt(HttpStatus.IM_A_TEAPOT_418, "I'm a teapot that only likes UUIDs.")

    val fileEntry = get { transaction { FileEntry.findById(uuid) } }
        .orHalt(HttpStatus.NOT_FOUND_404, "File with that UUID is not known.")

    return fileEntry.hits
}

fun dataFileSize(req: Request, resp: Response): Any {
    val fileSize = get { FileSize.parse(req.params(":type")) }
        .orHalt(HttpStatus.BAD_REQUEST_400, "Invalid file size type. Think kilobytes, mb, and related.")

    return File("${ServerConfig.databaseRootName}.mv.db").length().asFileSize(fileSize)
}

fun uploadedFileSize(req: Request, resp: Response): Any {
    val fileSize = get { FileSize.parse(req.params(":type")) }
        .orHalt(HttpStatus.BAD_REQUEST_400, "Invalid file size type. Think kilobytes and mb, and related.")

    val uuid = get { req.params(":id").toUUID() }
        .orHalt(HttpStatus.IM_A_TEAPOT_418, "I'm a teapot that only likes UUIDs.")

    val fileEntry = get { transaction { FileEntry.findById(uuid) } }
        .orHalt(HttpStatus.NOT_FOUND_404, "File with that UUID is not known.")


    return File(fileEntry.filePath).length().asFileSize(fileSize)
}


fun enablePublicAPI() {
    path("/api") {
        path("/:id") {
            get("/hits", ::hitsById)
            get("/size/:type", ::uploadedFileSize)
        }

        get("/database/:type", ::dataFileSize)

        get("/upload-count") { _, _ ->
            transaction { FileEntry.count() }
        }
    }
}