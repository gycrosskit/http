package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

fun createAndroidHttpClientEngine(): HttpClientEngine = OkHttp.create()
