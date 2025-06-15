package com.muort.upworker

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityKvManagerBinding
import okhttp3.*
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.RequestBody.Companion.toRequestBody

class KvManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKvManagerBinding
    private lateinit var account: Account

    private var namespaces: List<KvNamespace> = emptyList()
    private var selectedNamespace: KvNamespace? = null

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKvManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 读取账号信息
        val name = intent.getStringExtra("account_name") ?: ""
        val id = intent.getStringExtra("account_id") ?: ""
        val token = intent.getStringExtra("token") ?: ""
        account = Account(name, id, token, null)

        binding.resultText.movementMethod = ScrollingMovementMethod.getInstance()
        binding.resultText.setTextIsSelectable(true)

        // 命名空间相关
        binding.selectNamespaceBtn.setOnClickListener { showNamespaceDialog() }
        binding.refreshNamespaceBtn.setOnClickListener { loadNamespaces() }
        binding.createNamespaceBtn.setOnClickListener { createNamespace() }

        // KV操作
        binding.readBtn.setOnClickListener { readKey() }
        binding.writeBtn.setOnClickListener { writeKey() }
        binding.deleteBtn.setOnClickListener { deleteKey() }
        binding.listBtn.setOnClickListener { listKeys() }
        binding.deleteNamespaceBtn.setOnClickListener { deleteNamespace() }

        // 复制结果
        binding.copyResultBtn.setOnClickListener {
            val text = binding.resultText.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("响应结果", text)
            clipboard.setPrimaryClip(clip)
            showToast("已复制到剪贴板")
        }

        loadNamespaces()
    }

    private fun showNamespaceDialog() {
        if (namespaces.isEmpty()) {
            showToast("暂无命名空间")
            return
        }
        val names = namespaces.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择命名空间")
            .setItems(names) { _, which ->
                selectedNamespace = namespaces[which]
                binding.namespaceText.text = selectedNamespace?.title
            }
            .show()
    }

    private fun loadNamespaces() {
        binding.namespaceText.text = "加载中..."
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.namespaceText.text = "加载失败"
                    showResult("加载命名空间失败: ${e.message}")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                // 这里只做简单解析，实际应根据API返回格式处理
                // 假设返回 { result: [ { id, title }, ... ] }
                val list = KvNamespace.parseList(body)
                runOnUiThread {
                    namespaces = list
                    if (list.isNotEmpty()) {
                        selectedNamespace = list[0]
                        binding.namespaceText.text = selectedNamespace?.title
                    } else {
                        binding.namespaceText.text = "无命名空间"
                    }
                    showResult("命名空间加载完成，共${list.size}个")
                }
            }
        })
    }

    private fun createNamespace() {
        val edit = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("新建命名空间")
            .setView(edit)
            .setPositiveButton("创建") { _, _ ->
                val nsName = edit.text.toString().trim()
                if (nsName.isEmpty()) {
                    showToast("名称不能为空")
                    return@setPositiveButton
                }
                val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces"
                val json = """{"title":"$nsName"}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer ${account.token}")
                    .addHeader("Content-Type", "application/json")
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread { showResult("创建失败: ${e.message}") }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            showResult("创建命名空间返回: ${response.body?.string()}")
                            loadNamespaces()
                        }
                    }
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun readKey() {
        val ns = selectedNamespace ?: return showToast("请选择命名空间")
        val key = binding.keyEdit.text.toString().trim()
        if (key.isEmpty()) return showToast("请输入Key")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}/values/$key"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showResult("读取失败: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { showResult("读取结果: ${response.body?.string()}") }
            }
        })
    }

    private fun writeKey() {
        val ns = selectedNamespace ?: return showToast("请选择命名空间")
        val keyInput = binding.keyEdit.text.toString().trim()
        val value = binding.valueEdit.text.toString()

        // 判断keyEdit内容是否为JSON数组
        if (keyInput.startsWith("[")) {
            Thread {
                try {
                    val arr = JSONArray(keyInput)
                    val results = StringBuilder()
                    var success = 0
                    var fail = 0
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val k = obj.optString("name")
                        if (k.isNullOrEmpty()) {
                            results.append("第${i + 1}项缺少name字段，跳过\n")
                            fail++
                            continue
                        }
                        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}/values/$k"
                        val request = Request.Builder()
                            .url(url)
                            .put("".toRequestBody(null)) // value为空字符串
                            .addHeader("Authorization", "Bearer ${account.token}")
                            .build()
                        try {
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                results.append("写入 $k 成功\n")
                                success++
                            } else {
                                results.append("写入 $k 失败: ${response.body?.string()}\n")
                                fail++
                            }
                        } catch (e: Exception) {
                            results.append("写入 $k 异常: ${e.message}\n")
                            fail++
                        }
                    }
                    runOnUiThread {
                        showResult("批量写入完成，成功 $success 项，失败 $fail 项：\n$results")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showResult("批量写入解析失败: ${e.message}")
                    }
                }
            }.start()
            return
        }

        // 单个写入
        val key = keyInput
        if (key.isEmpty()) return showToast("请输入Key")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}/values/$key"
        val request = Request.Builder()
            .url(url)
            .put(value.toRequestBody(null))
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showResult("写入失败: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { showResult("写入结果: ${response.body?.string()}") }
            }
        })
    }

    private fun deleteKey() {
        val ns = selectedNamespace ?: return showToast("请选择命名空间")
        val key = binding.keyEdit.text.toString().trim()
        if (key.isEmpty()) return showToast("请输入Key")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}/values/$key"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showResult("删除失败: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { showResult("删除结果: ${response.body?.string()}") }
            }
        })
    }

    private fun listKeys() {
        val ns = selectedNamespace ?: return showToast("请选择命名空间")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}/keys"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showResult("列出失败: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string() ?: ""
                val formatted = try {
                    // Cloudflare 返回的是一个对象，里面有 result 字段
                    val json = JSONObject(raw)
                    if (json.has("result")) {
                        val arr = json.getJSONArray("result")
                        arr.toString(2) // 缩进2格
                    } else {
                        json.toString(2)
                    }
                } catch (e: Exception) {
                    raw // 解析失败就原样输出
                }
                runOnUiThread { showResult(formatted) }
            }
        })
    }

    private fun deleteNamespace() {
        val ns = selectedNamespace ?: return showToast("请选择命名空间")
        AlertDialog.Builder(this)
            .setTitle("确认删除？")
            .setMessage("确定要删除命名空间：${ns.title} 吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/storage/kv/namespaces/${ns.id}"
                val request = Request.Builder()
                    .url(url)
                    .delete()
                    .addHeader("Authorization", "Bearer ${account.token}")
                    .build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread { showResult("删除命名空间失败: ${e.message}") }
                    }
                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            showResult("删除命名空间结果: ${response.body?.string()}")
                            loadNamespaces()
                        }
                    }
                })
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showResult(msg: String) {
        binding.resultText.setText(msg)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

// 简单的数据类和解析方法
data class KvNamespace(val id: String, val title: String) {
    companion object {
        fun parseList(json: String): List<KvNamespace> {
            // 这里只做简单正则解析，建议用Gson等库
            val regex = Regex("""\{[^\}]*"id"\s*:\s*"([^"]+)"[^\}]*"title"\s*:\s*"([^"]+)"[^\}]*\}""")
            return regex.findAll(json).map {
                KvNamespace(it.groupValues[1], it.groupValues[2])
            }.toList()
        }
    }
}