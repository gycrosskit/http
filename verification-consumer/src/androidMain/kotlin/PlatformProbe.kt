package sample.http

import com.dgtang.live.data.remote.createAndroidHttpClientEngine

fun platformTransport() = transport(createAndroidHttpClientEngine()) { }
