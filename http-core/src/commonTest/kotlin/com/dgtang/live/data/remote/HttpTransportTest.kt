package com.dgtang.live.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpTransportTest {
    @Test
    fun retriesRequireExplicitOptInAndDoNotRepeatResponseDecoding() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val client = createHttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val count = (attempts[path] ?: 0) + 1
            attempts[path] = count
            respond("ok", if (path == "/default" || path == "/retry" && count == 1)
                io.ktor.http.HttpStatusCode.ServiceUnavailable else io.ktor.http.HttpStatusCode.OK)
        }).httpTransport(readResponseBody = { request, _ ->
            if (request.url.endsWith("/decode")) throw SerializationException("invalid response")
            byteArrayOf()
        })
        try {
            assertEquals(503, client.get("https://example.test/default").status)
            assertEquals(1, attempts["/default"])
            assertEquals(200, client.get("https://example.test/retry") { enableIdempotentRetry(1) }.status)
            assertEquals(2, attempts["/retry"])
            assertEquals(HttpFailureReason.INVALID_RESPONSE, assertFailsWith<HttpTransportException> {
                client.get("https://example.test/decode") { enableIdempotentRetry(2) }
            }.reason)
            assertEquals(1, attempts["/decode"])
        } finally { client.close() }
    }

    @Test
    fun hostInterceptorsInjectRequestsAndObserveSuccessOrFailureInReverseOrder() = runTest {
        val events = mutableListOf<String>()
        fun interceptor(name: String) = object : HttpInterceptor {
            override suspend fun onRequest(request: AppHttpRequest): AppHttpRequest {
                events += "$name.request"
                return if (name == "first") request.copy(
                    query = request.query + ("host" to listOf("shared")),
                    headers = request.headers + ("X-Host-Extension" to listOf("installed")),
                ) else {
                    assertEquals(listOf("shared"), request.query["host"])
                    assertEquals(listOf("installed"), request.headers["X-Host-Extension"])
                    request
                }
            }
            override suspend fun onResponse(request: AppHttpRequest, response: AppHttpResponse) {
                events += "$name.response"
                assertEquals(200, response.status)
                if (name == "first") error("log observer failed")
            }
            override suspend fun onFailure(request: AppHttpRequest, error: Throwable) {
                events += "$name.failure"
                assertEquals(HttpFailureReason.NETWORK_UNAVAILABLE, assertIs<HttpTransportException>(error).reason)
            }
        }
        val native = HttpClient(MockEngine { request ->
            assertEquals("shared", request.url.parameters["host"])
            assertEquals("installed", request.headers["X-Host-Extension"])
            assertEquals("caller", request.url.parameters["source"])
            events += "engine"
            assertTrue(request.url.encodedPath.startsWith("/base/"))
            if (request.url.encodedPath == "/base/failure") throw IOException("offline")
            respond("ok")
        })
        val client = native.httpTransport(baseUrl = { "https://example.test/base/" },
            interceptors = listOf(interceptor("first"), interceptor("second")))
        try {
            assertEquals("ok", client.get("success") { parameter("source", "caller") }.bodyAsText())
            assertEquals(listOf("first.request", "second.request", "engine", "second.response", "first.response"), events)
            events.clear()
            val failure = assertFailsWith<HttpTransportException> {
                client.get("failure") { parameter("source", "caller") }
            }
            assertEquals(HttpFailureReason.NETWORK_UNAVAILABLE, failure.reason)
            assertEquals(listOf("first.request", "second.request", "engine", "second.failure", "first.failure"), events)
        } finally { client.close() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun deadlinePreservesImmediateDeliveryAndDistinguishesExternalCancellation() = runTest {
        var delivered = false
        launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(7, withHttpTimeout(30_000) { 7 })
            delivered = true
        }
        assertTrue(delivered, "deadline cleanup must not delay Renderer state delivery")
        val timeout = assertFailsWith<HttpTransportException> { withHttpTimeout(10) { awaitCancellation() } }
        assertEquals(HttpFailureReason.TIMEOUT, timeout.reason)
        val external = CancellationException("caller stopped")
        val cancellation = assertFailsWith<CancellationException> { withHttpTimeout(30_000) { throw external } }
        assertTrue(generateSequence<Throwable>(cancellation) { it.cause }.any { it === external })
    }


    @Test
    fun independentClientUsesInjectedJsonAdapter() = runTest {
        val adapter = Json { encodeDefaults = false }
        val native = createHttpClient(MockEngine { request ->
            assertEquals("{}", request.body.toByteArray().decodeToString())
            respond(if (request.url.encodedPath == "/unknown") """{"extra":true}""" else "{}")
        }, adapter)
        val client = native.httpTransport(adapter = adapter)
        try {
            val response = client.post("https://example.test/") { setBody(Payload()) }
            assertSame(adapter, response.dataAdapter)
            assertEquals(Payload(), response.body<Payload>())
            val unknown = client.post("https://example.test/unknown") { setBody(Payload()) }
            assertFailsWith<SerializationException> { unknown.body<Payload>() }
        } finally { client.close() }
    }

    @Test
    fun interceptorCanExtendOrShortenTransferTimeoutAndPreparationRemainsBounded() = runTest {
        val native = createHttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/extended") {
                withContext(Dispatchers.Default) { delay(200) }
                respond("ok")
            } else awaitCancellation()
        })
        val client = native.httpTransport(interceptors = listOf(object : HttpInterceptor {
            override suspend fun onRequest(request: AppHttpRequest): AppHttpRequest = when {
                request.url.endsWith("/preparation") -> awaitCancellation()
                request.url.endsWith("/extended") -> request.copy(timeoutMillis = 5_000)
                else -> request.copy(timeoutMillis = 20)
            }
        }))
        try {
            assertEquals("ok", client.get("https://example.test/extended") { timeoutMillis = 50 }.bodyAsText())
            for (path in listOf("shortened", "preparation")) {
                val failure = assertFailsWith<HttpTransportException> {
                    client.get("https://example.test/$path") { timeoutMillis = if (path == "preparation") 20 else 5_000 }
                }
                assertEquals(HttpFailureReason.TIMEOUT, failure.reason)
            }
        } finally { client.close() }
    }

    @Test
    fun finalInterceptorConfigurationIsValidatedBeforeSending() = runTest {
        var sent = false
        val native = createHttpClient(MockEngine { sent = true; respond("unexpected") })
        val client = native.httpTransport(interceptors = listOf(object : HttpInterceptor {
            override suspend fun onRequest(request: AppHttpRequest) =
                if (request.url.endsWith("/timeout")) request.copy(timeoutMillis = 0) else request.copy(maxRetries = -1)
        }))
        try {
            for (path in listOf("timeout", "retries")) {
                assertFailsWith<HttpTransportException> { client.get("https://example.test/$path") }
            }
            assertEquals(false, sent)
        } finally { client.close() }
    }

    @Test
    fun callerCancellationPropagatesDuringPreparationAndTransfer() = runTest {
        for (duringPreparation in listOf(true, false)) {
            val entered = CompletableDeferred<Unit>()
            val native = createHttpClient(MockEngine { entered.complete(Unit); awaitCancellation() })
            val client = native.httpTransport(interceptors = listOf(object : HttpInterceptor {
                override suspend fun onRequest(request: AppHttpRequest): AppHttpRequest {
                    if (duringPreparation) { entered.complete(Unit); awaitCancellation() }
                    return request
                }
            }))
            try {
                val operation = launch { client.get("https://example.test/") }
                entered.await()
                operation.cancelAndJoin()
                assertTrue(operation.isCancelled)
            } finally { client.close() }
        }
    }

    @Test
    fun multipartSendsFieldsBytesAndStreamingSource() = runTest {
        var streamed = 0
        val native = createHttpClient(MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("Contents") && body.contains("message"))
            assertTrue(body.contains("bytes") && body.contains("stream"))
            respond("ok")
        })
        val client = native.httpTransport()
        try {
            client.post("https://example.test/") {
                setBody(HttpMultipartContent(mapOf("Contents" to "message"), listOf(
                    HttpMultipartFile("files", "bytes".encodeToByteArray(), "a.txt", "text/plain"),
                    HttpMultipartFile("files", byteArrayOf(), "b.txt", "text/plain",
                        stream = object : HttpUploadSource {
                            override val size = 6L
                            override suspend fun writeTo(write: suspend (ByteArray, Int, Int) -> Unit) {
                                streamed++
                                val bytes = "stream".encodeToByteArray()
                                write(bytes, 0, bytes.size)
                            }
                        }),
                )))
            }
            assertEquals(1, streamed)
        } finally { client.close() }
    }

    @Test
    fun hostHooksReceiveOriginalMetadataAndPreservePolicyFailure() = runTest {
        val key = HttpRequestAttributeKey<String>()
        val policyFailure = PolicyFailure()
        val native = createHttpClient(MockEngine { respond("raw") })
        val extension = object : HttpInterceptor {
            override suspend fun onRequest(request: AppHttpRequest) = request.copy(context = HttpRequestContext())
        }
        val client = native.httpTransport(interceptors = listOf(extension),
            configureRequest = { request, _ ->
                assertEquals("original", request.context.getOrNull(key))
                if (request.url.endsWith("/blocked")) throw policyFailure
            },
            readResponseBody = { _, _ -> "custom".encodeToByteArray() },
            preserveFailure = { it is PolicyFailure })
        try {
            assertEquals("custom", client.get("https://example.test/") { attributes.put(key, "original") }.bodyAsText())
            val thrown = assertFailsWith<PolicyFailure> {
                client.get("https://example.test/blocked") { attributes.put(key, "original") }
            }
            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === policyFailure })
        } finally { client.close() }
    }

    private class PolicyFailure : IllegalStateException("policy")

    @Serializable
    data class Payload(val value: Int = 7)
}
