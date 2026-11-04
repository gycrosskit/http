package com.dgtang.live.data.remote

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.readText
import kotlinx.io.Buffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 装配与测试可选择 Ktor 实现；Repository 只接收 AppHttpClient。 */
fun HttpClient.httpTransport(
    baseUrl: () -> String? = { null },
    interceptors: List<HttpInterceptor> = emptyList(),
    adapter: Json = AppJson,
    configureRequest: HttpRequestBuilder.(AppHttpRequest, AppHttpRequest) -> Unit = { _, _ -> },
    readResponseBody: suspend (AppHttpRequest, HttpResponse) -> ByteArray = { _, response -> response.body<ByteArray>() },
    preserveFailure: (Exception) -> Boolean = { false },
    onObserverFailure: (Exception) -> Unit = {},
): AppHttpClient = KtorHttpTransport(this, baseUrl, interceptors.toList(), adapter,
    configureRequest, readResponseBody, preserveFailure, onObserverFailure)

private class KtorHttpTransport(
    private val client: HttpClient,
    private val baseUrl: () -> String?,
    private val interceptors: List<HttpInterceptor>,
    override val dataAdapter: Json,
    private val configureRequest: HttpRequestBuilder.(AppHttpRequest, AppHttpRequest) -> Unit,
    private val readResponseBody: suspend (AppHttpRequest, HttpResponse) -> ByteArray,
    private val preserveFailure: (Exception) -> Boolean,
    private val onObserverFailure: (Exception) -> Unit,
) : AppHttpClient {
    override val coroutineContext get() = client.coroutineContext
    override fun close() = client.close()

    override suspend fun execute(request: AppHttpRequest): AppHttpResponse {
        require(request.timeoutMillis > 0 && request.maxRetries >= 0)
        var fixed = request
        try {
            // 初始化与拦截准备使用原预算；实际传输单独使用扩展后的预算，允许宿主缩短或延长超时。
            withHttpTimeout(request.timeoutMillis) {
                for (interceptor in interceptors) fixed = interceptor.onRequest(fixed)
                // 扩展不能替换调用方捕获的会话归属。
                fixed = fixed.copy(context = request.context)
            }
            require(fixed.timeoutMillis > 0 && fixed.maxRetries >= 0)
            return withHttpTimeout(fixed.timeoutMillis) {
                val response = client.prepareRequest {
                    expectSuccess = false
                    val base = baseUrl()?.takeIf(String::isNotBlank)
                    url(if (base != null && !fixed.url.contains("://")) base.trimEnd('/') + "/" + fixed.url.trimStart('/') else fixed.url)
                    method = HttpMethod(fixed.method)
                    fixed.query.forEach { (name, values) -> url.parameters.appendAll(name, values) }
                    fixed.headers.forEach { (name, values) -> headers.appendAll(name, values) }
                    when (val body = fixed.body) {
                        HttpBody.Empty -> Unit
                        is HttpBody.Text -> {
                            body.contentType?.takeIf { headers["Content-Type"] == null }?.let { contentType(ContentType.parse(it)) }
                            setBody(body.value)
                        }
                        is HttpMultipartContent -> {
                            setBody(ktorMultipartContent(body.fields, body.files, kotlinx.coroutines.CoroutineScope(currentCoroutineContext())))
                        }
                    }
                    configureRequest(this, fixed, request)
                    if (fixed.maxRetries > 0) enableIdempotentRetry(fixed.maxRetries)
                }.execute { response ->
                    val bytes = readResponseBody(fixed, response)
                    AppHttpResponse(response.status.value, response.headers.entries().associate { it.key to it.value },
                        bytes, fixed.method, response.call.request.url.toString(),
                        decodeNonUtf8Text(bytes, response.headers.entries().associate { it.key to it.value }), dataAdapter)
                }
                for (interceptor in interceptors.asReversed()) observe { interceptor.onResponse(fixed, response) }
                response
            }
        } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (preserveFailure(error)) throw error
                val failure = error as? HttpTransportException
                    ?: HttpTransportException(error.transportFailureReason())
                for (interceptor in interceptors.asReversed()) observe { interceptor.onFailure(fixed, failure) }
                throw failure
        }
    }

    private suspend fun observe(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { onObserverFailure(error) }
    }
}

/** 字符集实现留在传输层，不让业务依赖 Ktor Charset。UTF-8 不保留重复正文。 */
fun decodeNonUtf8Text(bytes: ByteArray, headers: Map<String, List<String>>): String? {
    val contentType = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.firstOrNull() ?: return null
    val charset = ContentType.parse(contentType).charset() ?: return null
    if (charset == Charsets.UTF_8) return null
    return Buffer().apply { write(bytes) }.readText(charset)
}

internal fun ktorMultipartContent(fields: Map<String, String>, files: List<HttpMultipartFile>, scope: CoroutineScope? = null) =
    MultiPartFormDataContent(formData {
        fields.forEach { (key, value) -> append(key, value) }
        files.forEach { file ->
            val headers = Headers.build {
                append(HttpHeaders.ContentType, file.contentType)
                append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
            }
            val source = file.stream
            if (source == null) append(file.field, file.bytes, headers)
            else append(file.field, ChannelProvider(source.size) {
                val channel = ByteChannel()
                checkNotNull(scope).launch {
                    try { source.writeTo { bytes, offset, count -> channel.writeFully(bytes, offset, count) }; channel.flushAndClose() }
                    catch (error: Throwable) { channel.cancel(error); throw error }
                }
                channel
            }, headers)
        }
    })
