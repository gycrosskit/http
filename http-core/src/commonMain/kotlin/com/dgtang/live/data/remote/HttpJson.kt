package com.dgtang.live.data.remote

import kotlinx.serialization.json.Json

/** 默认 JSON 适配；宿主与缓存复用同一配置，调用方可为独立 Client 注入其他 Json 配置。 */
val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

/** 网络错误不携带业务状态码或宿主会话类型。 */
enum class HttpFailureReason { TIMEOUT, NETWORK_UNAVAILABLE, SECURE_CONNECTION, INVALID_RESPONSE, UPLOAD_UNAVAILABLE }
