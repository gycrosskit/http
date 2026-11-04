package com.dgtang.live.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal actual fun Throwable.platformHttpFailureReason(): HttpFailureReason? = when {
    this is SocketTimeoutException -> HttpFailureReason.TIMEOUT
    this is SSLException -> HttpFailureReason.SECURE_CONNECTION
    this is UnknownHostException ||
        this is ConnectException ||
        this is NoRouteToHostException ||
        this is IOException -> HttpFailureReason.NETWORK_UNAVAILABLE
    else -> null
}
