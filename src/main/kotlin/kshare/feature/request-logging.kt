package kshare.feature

import kshare.*
import spark.Spark.*

// log HTTPMethod HTTPEndpoint -> HTTPStatus in console for requests.
// this does not log IP addresses to the console; only the HTTP Method used, the endpoint, and the HTTP status, no identifying information.
fun enableRequestLogger() {
    afterAfter { req, resp ->
        val location = buildString(ServerConfig.effectiveHost(req)) {
            appendIf(req.hasQueryString()) {
                "?${req.queryString()}"
            }
        }
        "Watchdog".logger().info("${req.requestMethod()} $location -> ${resp.status()}")
    }
}