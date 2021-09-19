package kshare

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import java.util.*

fun allTables(): Array<Table> = arrayOf(
    FileEntries
)

object FileEntries : UUIDTable() {
    val hits = integer("hits").default(0)
    val type = varchar("type", 50)
    val data = blob("data")
}

class FileEntry(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntry>(FileEntries)

    var hits by FileEntries.hits
    var type by FileEntries.type
    var data by FileEntries.data
}