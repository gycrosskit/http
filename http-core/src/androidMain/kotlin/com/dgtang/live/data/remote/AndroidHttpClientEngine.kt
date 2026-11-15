package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** 默认验证证书；历史服务兼容必须由宿主显式关闭，包含证书链与主机名。 */
fun createAndroidHttpClientEngine(): HttpClientEngine = createAndroidHttpClientEngine(true)

fun createAndroidHttpClientEngine(validateCertificates: Boolean): HttpClientEngine = OkHttp.create {
    if (!validateCertificates) {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        config {
            sslSocketFactory(context.socketFactory, trustManager)
            hostnameVerifier { _, _ -> true }
        }
    }
}
