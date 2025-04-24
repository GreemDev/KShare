package kshare.systems

import kshare.FileEntries
import kshare.FileEntry
import kshare.ServerConfig
import kshare.logger
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.Date
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.io.path.notExists
import kotlin.time.Duration.Companion.minutes

private val logger = "Doctor".logger()

private var cleanupTimer: Timer? = null

fun startUploadCleanSystem() {
    cleanupTimer = fixedRateTimer(
        "KShare Upload Cleaner",
        daemon = true,
        Date.from(Instant.now().plusSeconds(15)),
        period = 30.minutes.inWholeMilliseconds) { cleanupTask() }
    logger.info("Started database cleanup worker.")
}

private fun cleanupTask() {
    logger.info("Starting cleanup task...")
    logger.info("Scanning ${transaction { FileEntry.count() }} file entries.")

    val validUsernames = ServerConfig.getValidUsernames()

    val deleted = transaction {
        val entries = FileEntry.all()
        val uuids = mutableListOf<UUID>()
        for (fileEntry in entries) {
            if (fileEntry.resolveFilePath().notExists()) {
                logger.warn("${fileEntry.id.value}'s local file no longer exists. Queueing for deletion...")
                uuids.add(fileEntry.id.value)
            }

            if (fileEntry.uploader !in validUsernames) {
                logger.warn("${fileEntry.id.value}'s user is no longer in the configuration. Queueing for deletion...")
                uuids.add(fileEntry.id.value)
            }
        }
        if (uuids.isEmpty())
            return@transaction 0

        FileEntries.deleteWhere { id inList uuids }
    }

    if (deleted <= 0)
        return
    else if (deleted == 1) {
        logger.info("Cleanup completed. One file entry was purged.")
    } else
        logger.info("Cleanup completed. $deleted file entries were purged.")
}