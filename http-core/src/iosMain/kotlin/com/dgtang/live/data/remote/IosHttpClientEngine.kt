@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.create
import platform.Foundation.serverTrust

/** 默认使用系统信任；兼容开关只处理服务端证书，不接管密码或客户端证书挑战。 */
fun createIosHttpClientEngine(validateCertificates: Boolean = true): HttpClientEngine = Darwin.create {
    // 首次无线数据授权期间等待系统恢复连接，避免首个业务请求立即失败。
    configureSession {
        waitsForConnectivity = true
    }
    if (!validateCertificates) {
        handleChallenge { _, _, challenge, completion ->
            val trust = challenge.protectionSpace.serverTrust()
            if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust && trust != null) {
                completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.create(trust = trust))
            } else {
                completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
