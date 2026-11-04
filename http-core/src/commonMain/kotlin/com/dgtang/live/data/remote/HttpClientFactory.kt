package com.dgtang.live.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 三端共用连接配置；认证、隐私与日志策略由宿主安装，单个幂等请求才显式启用重试。 */
fun createHttpClient(engine: HttpClientEngine, adapter: Json = AppJson,
    configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(adapter) }
    install(HttpRequestRetry) { noRetry() }
    configure()
}
