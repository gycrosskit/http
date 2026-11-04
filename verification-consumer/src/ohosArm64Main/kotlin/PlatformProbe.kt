package sample.http

import com.dgtang.live.data.remote.createOhosHttpClientEngine

fun platformTransport() = transport(createOhosHttpClientEngine()) { }
