package sample.http

import com.dgtang.live.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json

// 不声明 Ktor 直接依赖，用公开签名检验 Maven api 传递闭包。
fun transport(engine: HttpClientEngine, configure: HttpClientConfig<*>.() -> Unit): AppHttpClient {
    val client: HttpClient = createHttpClient(engine, Json, configure)
    return client.httpTransport(
        configureRequest = { request, original -> headerForHost(request, original) },
        readResponseBody = { _: AppHttpRequest, response: HttpResponse -> byteArrayOf(response.status.value.toByte()) },
    )
}
private fun HttpRequestBuilder.headerForHost(request: AppHttpRequest, original: AppHttpRequest) {
    check(request.context === original.context)
}
suspend fun executeAndClose(client: AppHttpClient) {
    try { client.get("https://example.test") { parameter("page", 1) } }
    finally { client.close() }
}
