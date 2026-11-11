package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

/** Curl 的 sslVerify 同时控制证书链与主机名验证，默认保持开启。 */
fun createOhosHttpClientEngine(): HttpClientEngine = createOhosHttpClientEngine(true)

fun createOhosHttpClientEngine(validateCertificates: Boolean): HttpClientEngine = Curl.create {
    sslVerify = validateCertificates
}
