package com.dgtang.live.data.remote

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorAppTransportSecurityRequiresSecureConnection
import platform.Foundation.NSURLErrorCallIsActive
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorClientCertificateRejected
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorTimedOut

/** 按 Darwin 原始错误码分类，不依赖本地化异常文本。 */
internal actual fun Throwable.platformHttpFailureReason(): HttpFailureReason? =
    (this as? DarwinHttpRequestException)?.origin?.code?.let { code ->
        when (code) {
            NSURLErrorTimedOut -> HttpFailureReason.TIMEOUT
            NSURLErrorSecureConnectionFailed,
            NSURLErrorServerCertificateHasBadDate,
            NSURLErrorServerCertificateUntrusted,
            NSURLErrorServerCertificateHasUnknownRoot,
            NSURLErrorServerCertificateNotYetValid,
            NSURLErrorClientCertificateRejected,
            NSURLErrorClientCertificateRequired,
            NSURLErrorAppTransportSecurityRequiresSecureConnection,
            -> HttpFailureReason.SECURE_CONNECTION
            NSURLErrorCannotFindHost,
            NSURLErrorCannotConnectToHost,
            NSURLErrorDNSLookupFailed,
            NSURLErrorNetworkConnectionLost,
            NSURLErrorNotConnectedToInternet,
            NSURLErrorInternationalRoamingOff,
            NSURLErrorCallIsActive,
            NSURLErrorDataNotAllowed,
            -> HttpFailureReason.NETWORK_UNAVAILABLE
            else -> null
        }
    }
