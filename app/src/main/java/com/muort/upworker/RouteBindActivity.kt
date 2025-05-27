package com.muort.upworker

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityRouteBindBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class RouteBindActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteBindBinding
    private val client = OkHttpClient()
    private var currentAccount: Account? = null
    private val routeList = mutableListOf<Route>()
    private val workerList = mutableListOf<String>()  // 保存脚本列表

    data class Route(val id: String, val pattern: String, val script: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteBindBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resultText.movementMethod = ScrollingMovementMethod.getInstance()

        val name = intent.getStringExtra("account_name")
        val accountId = intent.getStringExtra("account_id")
        val token = intent.getStringExtra("token")
        val zoneId = intent.getStringExtra("zone_id")

        if (name == null || accountId == null || token == null) {
            showToast("账号信息无效")
            finish()
            return
        }

        currentAccount = Account(name = name, accountId = accountId, token = token, zoneId = zoneId)
        binding.accountText.text = "当前账号: ${currentAccount!!.name}"

        currentAccount?.let {
            loadWorkerList(it)
            loadCurrentRoutes(it)
        }

        binding.bindBtn.setOnClickListener { bindRoutes() }

        binding.deleteBtn.setOnClickListener {
            val routeId = binding.routeIdEdit.text.toString().trim()
            if (routeId.isNotEmpty()) deleteRoute(routeId)
        }

        binding.updateBtn.setOnClickListener {
            val routeId = binding.routeIdEdit.text.toString().trim()
            val pattern = binding.patternEdit.text.toString().trim()
            val script = binding.workerSpinner.selectedItem?.toString()?.trim() ?: ""
            if (routeId.isNotEmpty() && pattern.isNotEmpty() && script.isNotEmpty()) {
                updateRoute(routeId, pattern, script)
            }
        }

        binding.selectRouteBtn.setOnClickListener {
            if (routeList.isEmpty()) {
                showToast("暂无绑定项")
                return@setOnClickListener
            }

            val routeNames = routeList.map { "路径: ${it.pattern}\n脚本: ${it.script}" }.toTypedArray()

            android.app.AlertDialog.Builder(this)
                .setTitle("选择一个绑定项")
                .setItems(routeNames) { _, index ->
                    val route = routeList[index]
                    binding.routeIdEdit.setText(route.id)
                    binding.patternEdit.setText(route.pattern)
                    val pos = (0 until binding.workerSpinner.count).firstOrNull {
                        binding.workerSpinner.getItemAtPosition(it).toString() == route.script
                    } ?: -1
                    if (pos >= 0) binding.workerSpinner.setSelection(pos)
                }
                .show()
        }

        // 新增管理脚本按钮点击事件
        binding.manageScriptsBtn.setOnClickListener {
            if (workerList.isEmpty()) {
                showToast("没有可管理的脚本")
                return@setOnClickListener
            }
            showManageScriptsDialog()
        }
    }

    private fun loadWorkerList(account: Account) {
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/workers/scripts"
        val request = Request.Builder().url(url).get().addHeader("Authorization", "Bearer ${account.token}").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { showToast("获取 Worker 列表失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string() ?: return
                val names = mutableListOf<String>()
                try {
                    val root = JSONObject(json)
                    val result = root.getJSONArray("result")
                    for (i in 0 until result.length()) {
                        names.add(result.getJSONObject(i).getString("id"))
                    }
                } catch (e: Exception) {
                    runOnUiThread { showToast("解析 Worker 列表失败") }
                    return
                }
                runOnUiThread {
                    workerList.clear()
                    workerList.addAll(names)
                    val adapter = ArrayAdapter(this@RouteBindActivity, android.R.layout.simple_spinner_item, names)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.workerSpinner.adapter = adapter
                }
            }
        })
    }

    private fun showManageScriptsDialog() {
        val selectedItems = BooleanArray(workerList.size)
        android.app.AlertDialog.Builder(this)
            .setTitle("选择要删除的脚本")
            .setMultiChoiceItems(workerList.toTypedArray(), selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton("删除") { _, _ ->
                val toDelete = workerList.filterIndexed { index, _ -> selectedItems[index] }
                if (toDelete.isEmpty()) {
                    showToast("未选择任何脚本")
                    return@setPositiveButton
                }
                deleteScripts(toDelete)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteScripts(scripts: List<String>) {
        val account = currentAccount ?: return
        val token = account.token
        if (token.isEmpty()) return

        binding.resultText.append("开始删除脚本...\n")

        var successCount = 0
        var failCount = 0

        scripts.forEach { scriptName ->
            val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/workers/scripts/$scriptName"
            val request = Request.Builder().url(url).delete().addHeader("Authorization", "Bearer $token").build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        binding.resultText.append("删除失败：$scriptName，错误：${e.message}\n")
                        failCount++
                        checkDeleteComplete(successCount, failCount, scripts.size)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            binding.resultText.append("删除成功：$scriptName\n")
                            successCount++
                        } else {
                            val res = response.body?.string()
                            binding.resultText.append("删除失败：$scriptName\n返回：$res\n")
                            failCount++
                        }
                        checkDeleteComplete(successCount, failCount, scripts.size)
                    }
                }
            })
        }
    }

    // 删除完成后刷新列表
    private fun checkDeleteComplete(successCount: Int, failCount: Int, total: Int) {
        if (successCount + failCount >= total) {
            runOnUiThread {
                showToast("脚本删除完成（成功：$successCount，失败：$failCount）")
                currentAccount?.let { loadWorkerList(it) }
            }
        }
    }

    // ... 以下保持你原有代码不变 ...

    private fun loadCurrentRoutes(account: Account) {
        val url = "https://api.cloudflare.com/client/v4/zones/${account.zoneId}/workers/routes"
        val request = Request.Builder().url(url).get().addHeader("Authorization", "Bearer ${account.token}").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { appendResult("获取绑定列表失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                runOnUiThread {
                    try {
                        val root = JSONObject(res)
                        val result = root.getJSONArray("result")
                        routeList.clear()
                        for (i in 0 until result.length()) {
                            val obj = result.getJSONObject(i)
                            val id = obj.getString("id")
                            val pattern = obj.getString("pattern")
                            val script = obj.optString("script", "未绑定")
                            val route = Route(id, pattern, script)
                            routeList.add(route)
                            appendResult("ID: $id\n路径: $pattern\n绑定脚本: $script\n")
                        }
                    } catch (e: Exception) {
                        appendResult("解析失败：${e.message}")
                    }
                }
            }
        })
    }

    private fun bindRoutes() {
        val account = currentAccount ?: return
        val zoneId = account.zoneId ?: return
        val token = account.token
        val script = binding.workerSpinner.selectedItem?.toString()?.trim() ?: ""
        val patterns = binding.routesEdit.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (script.isEmpty() || patterns.isEmpty()) {
            showToast("请填写 Worker 名称 和 路由列表")
            return
        }

        binding.bindBtn.isEnabled = false
        binding.resultText.text = "正在绑定...\n"

        patterns.forEach { pattern ->
            val url = "https://api.cloudflare.com/client/v4/zones/$zoneId/workers/routes"
            val json = JSONObject().apply {
                put("pattern", pattern)
                put("script", script)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json").build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { appendResult("失败：$pattern\n错误：${e.message}\n") }
                }

                override fun onResponse(call: Call, response: Response) {
                    val resText = response.body?.string()
                    runOnUiThread {
                        if (response.isSuccessful) {
                            appendResult("成功绑定：$pattern\n返回：$resText\n")
                        } else {
                            appendResult("绑定失败：$pattern\n返回：$resText\n")
                        }
                    }
                }
            })
        }

        binding.bindBtn.isEnabled = true
    }

    private fun deleteRoute(routeId: String) {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/zones/${account.zoneId}/workers/routes/$routeId"
        val request = Request.Builder().url(url).delete().addHeader("Authorization", "Bearer ${account.token}").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { appendResult("删除失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        appendResult("删除成功：$routeId")
                    } else {
                        appendResult("删除失败：$routeId\n返回：$res")
                    }
                }
            }
        })
    }

    private fun updateRoute(routeId: String, pattern: String, script: String) {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/zones/${account.zoneId}/workers/routes/$routeId"
        val json = JSONObject().apply {
            put("pattern", pattern)
            put("script", script)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).put(body).addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/json").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { appendResult("更新失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        appendResult("更新成功：$routeId")
                    } else {
                        appendResult("更新失败：$routeId\n返回：$res")
                    }
                }
            }
        })
    }

    private fun appendResult(msg: String) {
        binding.resultText.append(msg + "\n")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}