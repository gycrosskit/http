package sample.http

import com.dgtang.live.data.remote.createOhosHttpClientEngine

fun platformTransport() = transport(createOhosHttpClientEngine()) { }

fun compatibilityTransport() = transport(createOhosHttpClientEngine(validateCertificates = false)) { }
