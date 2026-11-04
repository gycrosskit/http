package com.dgtang.live.data.remote

import io.ktor.client.plugins.retry
import io.ktor.client.request.HttpRequestBuilder

/**
 * 为确认幂等的单个请求启用 Ktor 官方重试策略。
 *
 * [maxRetries] 是首次请求后的额外尝试次数。网络异常、连接/请求超时与 HTTP 5xx 会重试；调用方取消、
 * HTTP 4xx 以及响应解析错误不会重试。提交、支付、领取等非幂等请求不得调用此扩展。
 */
fun HttpRequestBuilder.enableIdempotentRetry(maxRetries: Int = DEFAULT_MAX_RETRIES) {
    retry {
        retryOnServerErrors(maxRetries = maxRetries)
        retryOnException(maxRetries = maxRetries, retryOnTimeout = true)
        exponentialDelay(
            baseDelayMs = RETRY_BASE_DELAY_MS,
            maxDelayMs = RETRY_MAX_DELAY_MS,
            randomizationMs = 0L,
        )
    }
}

private const val DEFAULT_MAX_RETRIES = 3
private const val RETRY_BASE_DELAY_MS = 200L
private const val RETRY_MAX_DELAY_MS = 800L
