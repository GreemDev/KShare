package kshare.feature

import daggerok.extensions.html.dom.*
import kshare.*
import org.eclipse.jetty.http.HttpStatus.*
import spark.Spark.*
import java.io.File
import java.io.OutputStream

private fun sanitizeFileLocation(loc: String) = when {
    loc.startsWith("../") -> loc.trimStart('.', '.', '/')
    loc.startsWith("./") -> loc.trimStart('.', '/')
    else -> loc
}.prependIndent("staticFiles/")

fun fileFrom(location: String): File = File(
    sanitizeFileLocation(location)
).also {
    if (!it.exists())
        throw IllegalArgumentException("Specified file not found on file system.")
}


fun enableStaticFileServer() {
    get("fs/*") { req, resp ->
        val file = get {
            fileFrom(req.splat().first())
        } orHalt { cause ->
            h1 {
                text(cause?.message ?: "Internal Error")
            }
            NOT_FOUND_404
        }

        if (File("staticFiles/").absolutePath !in file.absolutePath) {
            with("Test".logger()) {
                info("f: ${file.absolutePath}")
                info("d: ${File("staticFiles/").absolutePath}")
            }
            halt {
                h1 {
                    text("Invalid result path.")
                }
                BAD_REQUEST_400
            }
        }

        file.inputStream().copyTo(resp.raw().outputStream)

        resp.raw().outputStream.also(OutputStream::flush)
    }
}
