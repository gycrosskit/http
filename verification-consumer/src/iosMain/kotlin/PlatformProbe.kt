package sample.http

import com.dgtang.live.data.remote.createIosHttpClientEngine

fun platformTransport() = transport(createIosHttpClientEngine()) { }
