package com.muort.upworker

import android.content.Context
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

object WebDavUtils {

    private val client = OkHttpClient()

    // 上传文件到 WebDAV
    fun uploadToWebDav(context: Context, file: File, onResponse: (Boolean, String) -> Unit) {
        val (url, username, password) = WebDavConfig.load(context)

        if (url == null || username == null || password == null) {
            onResponse(false, "WebDAV 配置信息不完整")
            return
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = file.asRequestBody(mediaType) // 替代 create

        val request = Request.Builder()
            .url("$url/accounts_backup.json")
            .put(requestBody)
            .header("Authorization", Credentials.basic(username, password))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResponse(false, "上传失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    onResponse(true, "上传成功")
                } else {
                    onResponse(false, "上传失败: ${response.message}")
                }
            }
        })
    }

    // 从 WebDAV 下载文件
    fun downloadFromWebDav(context: Context, fileName: String, onResponse: (File?) -> Unit) {
        val (url, username, password) = WebDavConfig.load(context)

        if (url == null || username == null || password == null) {
            onResponse(null)
            return
        }

        val request = Request.Builder()
            .url("$url/$fileName")
            .get()
            .header("Authorization", Credentials.basic(username, password))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResponse(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val file = File(context.cacheDir, fileName)
                    val body = response.body
                    if (body != null) {
                        file.writeBytes(body.bytes())
                    }
                    onResponse(file)
                } else {
                    onResponse(null)
                }
            }
        })
    }
}