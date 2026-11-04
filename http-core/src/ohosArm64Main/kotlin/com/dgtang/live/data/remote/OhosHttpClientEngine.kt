package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.curl.Curl

fun createOhosHttpClientEngine(): HttpClientEngine = Curl.create()
