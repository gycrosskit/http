package com.dgtang.live.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

fun createIosHttpClientEngine(): HttpClientEngine = Darwin.create {
    // 首次无线数据授权期间等待系统恢复连接，避免首个业务请求立即失败。
    configureSession {
        waitsForConnectivity = true
    }
}
