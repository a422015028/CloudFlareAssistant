package com.muort.upworker

data class DnsRecord(
    val id: String,
    var type: String,
    var name: String,
    var content: String,
    var proxied: Boolean,
    var ttl: Int
)