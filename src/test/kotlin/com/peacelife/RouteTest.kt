package com.peacelife

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.PublishResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.natpryce.hamkrest.assertion.assertThat
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Header
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class RouteTest {

    @MockK
    lateinit var mockSns : AmazonSNS
    private val requestSpy: CapturingSlot<PublishRequest> = slot()
    private val originHeader = Header.required("Origin")

    init {
        System.setProperty("EVENT_TOPIC", "sns:arn")
        MockKAnnotations.init(this)
        every { mockSns.publish(capture(requestSpy)) } returns PublishResult()
    }

    @Test
    fun shouldCallSns() {
        val messageBody = MessageBody(name = "David", email = "my.email", message = "A message")
        val post = Request(Method.POST, "/contact")
                .body(jacksonObjectMapper().writeValueAsString(messageBody))
                .with( originHeader of ORIGIN)

        val response = internalRoute(mockSns).invoke(post)

        verify(exactly = 1) { mockSns.publish(any()) }
        assertThat( response, hasStatus(Status.OK))
        requestSpy.captured.message.split('\n').forEachIndexed { index, s ->
            when(index) {
                0 -> assertTrue { s.contains("Contact page submitted at ") }
                1 -> assertTrue { s.contains("Name: David") }
                2 -> assertTrue { s.contains("Email: my.email") }
                3 -> assertTrue { s.contains("Message: A message") }
            }
        }
    }

    @Test
    fun shouldHandleBadBody() {
        val post = Request(Method.POST, "/contact").body("{\"name\":\"David\"}")
                .with( originHeader of ORIGIN)
        val response = internalRoute(mockSns).invoke(post)

        verify(exactly = 0) { mockSns.publish(any()) }
        assertThat(response, hasStatus(Status.BAD_REQUEST))

    }

    @Test
    fun shouldReturn412WhenOriginNotCorrect() {
        val post = Request(Method.POST, "/contact")
                .with( originHeader of "blah")
        val response = internalRoute(mockSns).invoke(post)
        verify(exactly = 0) { mockSns.publish(any()) }
        assertThat( response, hasStatus(Status.PRECONDITION_FAILED))
    }

    @Test
    fun shouldReturn412WhenOriginNotIncluded() {
        val post = Request(Method.POST, "/contact")
        val response = internalRoute(mockSns).invoke(post)
        verify(exactly = 0) { mockSns.publish(any()) }
        assertThat( response, hasStatus(Status.PRECONDITION_FAILED))
    }
}
