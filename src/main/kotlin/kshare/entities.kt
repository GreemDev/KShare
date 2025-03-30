package kshare

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import java.util.*

fun createTables() = loggedTransaction {
    SchemaUtils.create(FileEntries, ShortenedURLs)
}

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

object ShortenedURLs : UUIDTable() {
    val hits = integer("hits").default(0)
    val longUrl = varchar("url", 500)
}

class ShortURL(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ShortURL>(ShortenedURLs)

    var hits by ShortenedURLs.hits
    var longUrl by ShortenedURLs.longUrl

}



