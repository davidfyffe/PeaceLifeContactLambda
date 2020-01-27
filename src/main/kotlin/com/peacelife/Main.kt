package com.peacelife

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import org.http4k.core.*
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.format.Jackson.auto
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.serverless.AppLoader
import org.joda.time.DateTime

data class MessageBody(val name: String, val email: String, val message: String)

object PeaceLifeLambda : AppLoader {
    override fun invoke(env: Map<String, String>): HttpHandler {
        return internalRoute()
    }
}

fun internalRoute(sns: AmazonSNS = AmazonSNSClientBuilder.standard().build()): RoutingHttpHandler {
    return routes(
            "/contact" bind Method.POST to invokeSNS(sns)
    )
}

private fun invokeSNS(sns : AmazonSNS): HttpHandler = { request: Request ->
    println("Dumping headers for logs")
    println(request.headers)

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
        }

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

