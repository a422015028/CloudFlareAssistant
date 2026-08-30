package com.muort.upworker.core.net.security

import java.io.IOException

/**
 * 远程文件下载 Wn 管线抛出的分类异常。
 *
 * 由于 HardcodedText=error lint 限制，构造 message **必须**通过
 * `context.getString(R.string.remote_url_security_xxx, ...)` 注入，严禁硬编码。
 */
class RemoteFileDownloadSecurityException(
    val code: RemoteSecurityCode,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
