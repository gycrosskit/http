package com.dgtang.live.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** 业务只依赖此契约；真实连接、桥接和平台异常由实现持有。 */
interface AppHttpClient : CoroutineScope, AutoCloseable {
    val dataAdapter: kotlinx.serialization.json.Json get() = AppJson
    suspend fun execute(request: AppHttpRequest): AppHttpResponse
    override fun close()
}

class HttpRequestContext {
    private val values = mutableMapOf<HttpRequestAttributeKey<*>, Any>()
    fun <T : Any> put(key: HttpRequestAttributeKey<T>, value: T) { values[key] = value }
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: HttpRequestAttributeKey<T>): T? = values[key] as T?
}
class HttpRequestAttributeKey<T : Any>

data class AppHttpRequest(
    val url: String,
    val method: String = "GET",
    val query: Map<String, List<String>> = emptyMap(),
    val headers: Map<String, List<String>> = emptyMap(),
    val body: HttpBody = HttpBody.Empty,
    val maxRetries: Int = 0,
    val timeoutMillis: Long = 30_000,
    val context: HttpRequestContext = HttpRequestContext(),
)

data class AppHttpResponse(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val bytes: ByteArray = byteArrayOf(),
    val requestMethod: String = "",
    val requestUrl: String = "",
    val decodedText: String? = null,
    val dataAdapter: kotlinx.serialization.json.Json = AppJson,
)

sealed interface HttpBody {
    data object Empty : HttpBody
    class Text(val value: String, val contentType: String? = null) : HttpBody
}

interface HttpUploadSource {
    val size: Long?
    suspend fun writeTo(write: suspend (ByteArray, Int, Int) -> Unit)
}

/** 注册者为宿主；取消继续传播，观察回调不得消费正文或自行重发请求。 */
interface HttpInterceptor {
    suspend fun onRequest(request: AppHttpRequest): AppHttpRequest = request
    suspend fun onResponse(request: AppHttpRequest, response: AppHttpResponse) = Unit
    suspend fun onFailure(request: AppHttpRequest, error: Throwable) = Unit
}

class AppHttpRequestBuilder(var url: String, val method: String,
    @PublishedApi internal val dataAdapter: kotlinx.serialization.json.Json = AppJson) {
    private val query = linkedMapOf<String, MutableList<String>>()
    private val headers = linkedMapOf<String, MutableList<String>>()
    @PublishedApi internal var requestBody: HttpBody = HttpBody.Empty
    val attributes = HttpRequestContext()
    internal var retries = 0
    var timeoutMillis: Long = 30_000
    fun parameter(name: String, value: Any?) { if (value != null) query.getOrPut(name) { mutableListOf() }.add(value.toString()) }
    fun header(name: String, value: Any?) { if (value != null) headers.getOrPut(name) { mutableListOf() }.add(value.toString()) }
    fun contentType(value: String) { headers["Content-Type"] = mutableListOf(value) }
    fun setBody(value: HttpBody) { requestBody = value }
    fun setBody(value: String) { requestBody = HttpBody.Text(value) }
    inline fun <reified T> setBody(value: T) { requestBody = HttpBody.Text(dataAdapter.encodeToString(value), "application/json") }
    fun enableIdempotentRetry(maxRetries: Int = 3) { require(maxRetries >= 0); retries = maxRetries }
    fun build() = AppHttpRequest(url, method, query.mapValues { it.value.toList() },
        headers.mapValues { it.value.toList() }, requestBody, retries, timeoutMillis, attributes)
}

suspend fun AppHttpClient.get(url: String, block: AppHttpRequestBuilder.() -> Unit = {}): AppHttpResponse =
    execute(AppHttpRequestBuilder(url, "GET", dataAdapter).apply(block).build())

suspend fun AppHttpClient.post(url: String, block: AppHttpRequestBuilder.() -> Unit = {}): AppHttpResponse =
    execute(AppHttpRequestBuilder(url, "POST", dataAdapter).apply(block).build())

fun AppHttpResponse.bodyAsText(): String = decodedText ?: bytes.decodeToString()
inline fun <reified T> AppHttpResponse.body(adapter: kotlinx.serialization.json.Json = dataAdapter): T = adapter.decodeFromString(bodyAsText())

/** 不向业务传播引擎特有异常。 */
class HttpTransportException(val reason: HttpFailureReason?, cause: Throwable? = null) :
    Exception("HTTP transport failed: ${reason?.name ?: "UNKNOWN"}", cause)

/** 定时不占 Renderer 调度器；请求保留调用方上下文，取消只结束本次操作。 */
suspend fun <T> withHttpTimeout(timeoutMillis: Long, block: suspend () -> T): T {
    require(timeoutMillis > 0)
    val timeout = CancellationException("HTTP deadline exceeded")
    try {
        return coroutineScope {
            val operation = async(start = CoroutineStart.UNDISPATCHED) { block() }
            // 计时器独立调度并在 finally 撤销；不等待其调度收尾，避免延后 Renderer 的结果交付。
            val timer = CoroutineScope(Dispatchers.Default)
            timer.launch { delay(timeoutMillis); operation.cancel(timeout) }
            try { operation.await() } finally { timer.cancel() }
        }
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        var cause: Throwable? = cancelled
        val visited = mutableSetOf<Throwable>()
        while (cause != null && visited.add(cause)) {
            if (cause === timeout) throw HttpTransportException(HttpFailureReason.TIMEOUT)
            cause = cause.cause
        }
        throw cancelled
    }
}
