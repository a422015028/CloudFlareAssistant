package com.muort.upworker.core.net.security

/**
 * 远程文件下载 Wn 管线的分类错误码。
 * 每个枚举都必须在 values/strings.xml + values-en/strings.xml 中有对应
 * `remote_url_security_<lower_snake>` 资源条目。
 */
enum class RemoteSecurityCode {
    /** URL 格式非法，不是 http(s) URI */
    INVALID_URL_FORMAT,
    /** URL 非 HTTPS 协议 */
    NOT_HTTPS,
    /** SSRF 防护：解析后的任一 IP 落在私网/保留网段 */
    SSRF_BLOCKED,
    /** host 不在 allowlist（allowlist 非空时） */
    HOST_NOT_IN_ALLOWLIST,
    /** 手动 follow 超过 5 跳 */
    TOO_MANY_REDIRECTS,
    /** 重定向 Location 指向非 HTTPS */
    REDIRECT_TO_NON_HTTPS,
    /** 2xx 响应的 Content-Type 不在白名单 */
    CONTENT_TYPE_DISALLOWED,
    /** Worker 脚本超过 5 MiB 上限 */
    SCRIPT_SIZE_EXCEEDED_5MIB,
    /** Pages zip 超过 25 MiB 上限 */
    ZIP_SIZE_EXCEEDED_25MIB,
    /** zip 首 2 字节不是 0x504B ("PK") */
    ZIP_MAGIC_MISMATCH,
    /** 非 2xx HTTP 状态码（非重定向） */
    HTTP_STATUS_ERROR,
    /** 底层 IO 错误（DNS / TLS / socket 等） */
    FETCH_IO_ERROR;
}
