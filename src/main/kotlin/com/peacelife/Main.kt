package com.peacelife

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import org.http4k.core.*
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PRECONDITION_FAILED
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.auto
import org.http4k.lens.Header
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import org.http4k.serverless.AppLoader
import org.joda.time.DateTime

data class MessageBody(val name: String, val email: String, val message: String)

fun main() {
    internalRoute().asServer(SunHttp(1234))
}

object PeaceLifeLambda : AppLoader {
    override fun invoke(env: Map<String, String>): HttpHandler {
        return internalRoute()
    }
}

const val ORIGIN = "https://davidfyffe.github.io"

val corsPolicy : CorsPolicy =
        CorsPolicy(origins = listOf(ORIGIN),
                headers = listOf("content-type", "Origin"),
                methods = listOf(Method.POST, Method.OPTIONS))

// Default CORS behaviour will set access-control-allow-origin header to string 'null' if the Origin is not expected.
// However, it will by default run the request, just deny the sender the response. Fuck that.
val myCorsFilter = Filter { next: HttpHandler ->
    { request: Request ->
        val allowedOrigins = Header.required("access-control-allow-origin")
        val origin = allowedOrigins(request)
        if(origin == "null") Response(PRECONDITION_FAILED).body("Incorrect CORS origin supplied") else next(request)
    }
}

fun internalRoute(sns: AmazonSNS = AmazonSNSClientBuilder.standard().build()): RoutingHttpHandler {
    return ServerFilters.Cors(corsPolicy).then(myCorsFilter).then(
            routes(
                    "/contact" bind Method.POST to invokeSNS(sns)
            )
    )
}

private fun invokeSNS(sns : AmazonSNS): HttpHandler = { request: Request ->
    println("Request Headers: ${request.headers}")

    try {
        val bodyLens = Body.auto<MessageBody>().toLens()
        val messageBody = bodyLens(request)

        val emailMessage = """
        Contact page submitted at ${DateTime.now()}
        Name: ${messageBody.name}
        Email: ${messageBody.email}
        Message: ${messageBody.message}
    """.trimIndent()

        sns.run {
            publish(
                    PublishRequest(getProperty("EVENT_TOPIC"), emailMessage)
            )
        }.run(::println)
        Response(OK).body("Message Sent.")
    } catch (exception: java.lang.Exception) {
        println("Exception in lambda with request ${request.bodyString()} \n ${exception.message} \n ${exception.stackTrace.asSequence().joinToString("\n")}")
        Response(BAD_REQUEST).body("Error sending message.")
    }
}

fun getProperty(property: String) = try {
    System.getenv()[property] ?: "not found"
} catch (exception: Exception) {
    println("Property $property not found in Environment ${exception.message ?: "null exception message"}")
    "Property $property not found in Environment"
}

