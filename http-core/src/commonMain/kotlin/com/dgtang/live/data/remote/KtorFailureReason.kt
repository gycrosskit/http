package com.dgtang.live.data.remote

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** 引擎与平台异常在传输边界分类，UI 和业务只消费稳定原因。 */
fun Throwable.transportFailureReason(): HttpFailureReason? {
    var current: Throwable? = this
    val visited = mutableSetOf<Throwable>()
    val platformReasons = mutableSetOf<HttpFailureReason>()
    var ioFailure = false
    while (current != null && visited.add(current)) {
        when (current) {
            is HttpTransportException -> return current.reason
            is HttpRequestTimeoutException -> return HttpFailureReason.TIMEOUT
            is ContentConvertException, is SerializationException -> return HttpFailureReason.INVALID_RESPONSE
        }
        current.platformHttpFailureReason()?.let(platformReasons::add)
        if (current is IOException) ioFailure = true
        current = current.cause
    }
    return when {
        HttpFailureReason.TIMEOUT in platformReasons -> HttpFailureReason.TIMEOUT
        HttpFailureReason.SECURE_CONNECTION in platformReasons -> HttpFailureReason.SECURE_CONNECTION
        HttpFailureReason.NETWORK_UNAVAILABLE in platformReasons || ioFailure -> HttpFailureReason.NETWORK_UNAVAILABLE
        else -> null
    }
}

internal expect fun Throwable.platformHttpFailureReason(): HttpFailureReason?
