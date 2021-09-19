package kshare

import org.jetbrains.exposed.sql.transactions.transaction
import spark.HaltException
import spark.Request
import spark.Response
import spark.Spark.*
import java.util.*


fun Response.halt(status: Int, body: String): HaltException {
    status(status)
    return halt(status, body)
}

fun hitsById(req: Request, resp: Response): Any {
    val uuid = req.params(":id").toUUID()
    if (uuid == null) {
        resp.halt(418, "I'm a teapot that only likes UUIDs.")
    }
    val fileEntry = transaction { FileEntry.findById(uuid!!) }

    if (fileEntry == null)
        resp.halt(404, "File with that UUID is not known.")

    return fileEntry!!.hits
}

fun uploadCount(req: Request, resp: Response): Any = transaction { FileEntry.count() }


fun enablePublicAPI() {
    path("/api") {
        path("/hits") {
            get("/:id", ::hitsById)
        }

        get("/upload-count", ::uploadCount)
    }
}