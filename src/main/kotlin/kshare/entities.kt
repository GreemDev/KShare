package kshare

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SchemaUtils
import java.util.*

fun createTables() = loggedTransaction {
    SchemaUtils.create(FileEntries)
}

object FileEntries : UUIDTable() {
    val hits: Column<Int>  = integer(::hits.name).default(0)
    val type: Column<String>  = varchar(::type.name, 50)
    val path: Column<String> = varchar(::path.name, 350)
    val uploader: Column<String> = varchar(::uploader.name, 100)
}

class FileEntry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntry>(FileEntries)

    var hits by FileEntries.hits
    var type by FileEntries.type
    var filePath by FileEntries.path
    var uploader by FileEntries.uploader
}



