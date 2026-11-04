package com.dgtang.live.data.remote


data class HttpMultipartFile(
    val field: String,
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val stream: HttpUploadSource? = null,
)

/** 只描述字段、字节或流式上传源，不持有宿主文件句柄或权限。 */
class HttpMultipartContent(
    val fields: Map<String, String>,
    val files: List<HttpMultipartFile>,
) : HttpBody
