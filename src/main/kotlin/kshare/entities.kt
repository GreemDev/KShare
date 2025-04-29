package kshare

import org.eclipse.jetty.http.HttpStatus
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import spark.Request
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes

fun createTables() = loggedTransaction {
    SchemaUtils.create(FileEntries)
}

object FileEntries : UUIDTable() {
    val hits: Column<Int>  = integer(::hits.name).default(0)
    val type: Column<String>  = varchar(::type.name, 50)
    val path: Column<String> = varchar(::path.name, 350)
    val uploader: Column<String> = varchar(::uploader.name, 100)

    fun writeUpload(req: Request): FileEntry {
        val filePart = get { req.raw().getPart("file") }
            .orHalt(HttpStatus.BAD_REQUEST_400, "File form name configured in ShareX should be \"file\"; nothing else.")

        val uploader = ServerConfig.getUsername(req["Authorization"])

        val uuid = UUID.randomUUID()
        val uploadFolder = getUserFolder(uploader).getUploadFolder(uuid)

        val newFile = uploadFolder.resolve(filePart.submittedFileName)
        newFile.writeBytes(filePart.inputStream.readBytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

        return loggedTransaction {
            FileEntry.new(uuid) {
                type = filePart.contentType
                filePath = newFile.relativeTo(globalUploadsFolder).toString()
                this.uploader = uploader
            }
        }
    }
}

class FileEntry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntry>(FileEntries)

    var hits by FileEntries.hits
    var type by FileEntries.type
    var filePath by FileEntries.path
    var uploader by FileEntries.uploader

    fun uploadedFileName(): Path = resolveFilePath().fileName

    fun resolveFilePath(): Path = Path("uploads/").resolve(filePath)
    fun tryReadFileBytes() = resolveFilePath().takeIf { it.exists() }?.readBytes()
}

val globalUploadsFolder = Path("uploads/").apply {
    if (notExists())
        createDirectory()
}

fun getUserFolder(username: String): Path = globalUploadsFolder.resolve(username).apply {
    if (notExists())
        createDirectory()
}

fun Path.getUploadFolder(uuid: UUID): Path = resolve(uuid.shorten()).apply {
    if (notExists())
        createDirectory()
}
