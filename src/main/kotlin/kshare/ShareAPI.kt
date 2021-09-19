package kshare

import org.jetbrains.exposed.sql.transactions.transaction
import spark.HaltException
import spark.Response
import spark.Spark
import java.util.*


fun Response.halt(status: Int, body: String): HaltException {
    status(status)
    return Spark.halt(status, body)
}


fun enablePublicAPI() {
    Spark.get("/api/hits/:id") { req, resp ->
        val uuid = tryOrNull { UUID.fromString(req.params(":id")) }
        if (uuid == null) {
            resp.halt(418, "I'm a teapot that only likes valid UUIDs.")
        }
        val fileEntry = transaction { FileEntry.findById(uuid!!) }

        if (fileEntry == null)
            resp.halt(404, "File with that UUID is not known.")

        fileEntry!!.hits
    }

    Spark.get("/api/upload-count") { _, _ ->
        transaction { FileEntry.count() }
    }
}