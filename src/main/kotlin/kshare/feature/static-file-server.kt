package kshare.feature

import daggerok.extensions.html.dom.*
import kshare.*
import kshare.util.areAnyParentsHidden
import kshare.util.get
import kshare.util.halt
import org.eclipse.jetty.http.HttpStatus.*
import spark.Spark.*
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private fun sanitizeFileLocation(loc: String) = when {
    loc.startsWith("../") -> loc.replace("../", "")
    loc.startsWith("./") -> loc.replace("./", "")
    else -> loc
}.prependIndent("staticFiles/")

fun fileFrom(location: String): File = File(
    sanitizeFileLocation(location)
).also {
    if (!it.exists())
        throw IllegalArgumentException("Specified file not found on file system.")
}


fun enableStaticFileServer() {
    if (ServerConfig.shouldAllowStaticFileDiscovery) {
        get("fs") { req, resp ->
            resp.body(html {
                val path = Path("staticFiles/")

                val files = Files.walk(path)
                    .map(Path::toFile)
                    .filter { file ->
                        file.canShow()
                    }!!.collect(Collectors.toList())

                files.forEachIndexed { idx, file ->
                    a(
                        "href" to "${ServerConfig.effectiveHost(req)}${file.absolutePath.replace(path.absolutePathString(), "")}"
                    ) {
                        text("${file.absolutePath.replace(path.absolutePathString(), "").replace('\\', '/')} (${file.extension.uppercase()})")
                    }
                    br()
                    br()
                }
            })
        }
    }

    get("fs/*") { req, resp ->
        val file = get {
            fileFrom(req.splat().first())
        } orHalt { cause ->
            h1 {
                text(cause?.message ?: "Internal Error")
            }
            NOT_FOUND_404
        }

        if (!file.canShow()) {
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

fun File.canShow() =
    File("staticFiles/").absolutePath in absolutePath &&
            !isHidden &&
            !isDirectory &&
            !areAnyParentsHidden &&
            name !in ServerConfig.blacklistedStaticFiles

