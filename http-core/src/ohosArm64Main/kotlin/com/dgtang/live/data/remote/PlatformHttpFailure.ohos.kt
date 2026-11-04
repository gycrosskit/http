package com.dgtang.live.data.remote


// 鸿蒙异常先由 commonMain 的 Ktor/IOException 分类；出现独有网络异常时再补专属映射。
internal actual fun Throwable.platformHttpFailureReason(): HttpFailureReason? = null
