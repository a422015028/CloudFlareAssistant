package com.muort.upworker

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    // ===== 自定义域 =====
    data class WorkerDomain(val id: String, val hostname: String, val service: String, val zoneId: String?)
    private val domainList = mutableListOf<WorkerDomain>()

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

        // ========= 模式切换（你需要在 XML 里加 bindModeGroup/routeLayout/domainLayout 等控件） =========
        binding.bindModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.routeModeBtn.id) {
                binding.routeLayout.visibility = View.VISIBLE
                binding.domainLayout.visibility = View.GONE
            } else {
                binding.routeLayout.visibility = View.GONE
                binding.domainLayout.visibility = View.VISIBLE
                loadDomainsForSelectedWorker()
            }
        }

        // Worker 切换时，如果当前在“自定义域”模式，刷新自定义域列表
        binding.workerSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            if (binding.domainLayout.visibility == View.VISIBLE) {
                loadDomainsForSelectedWorker()
            }
        }

        // ========= 原有路由逻辑 =========
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
            AlertDialog.Builder(this)
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

        binding.manageScriptsBtn.setOnClickListener {
            if (workerList.isEmpty()) {
                showToast("没有可管理的脚本")
                return@setOnClickListener
            }
            showManageScriptsDialog()
        }

        // ========= 自定义域：列表 Adapter =========
        binding.domainListView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf<String>() // 先占位，真正数据在 refreshDomainListUI()
        )

        // 自定义域：绑定按钮
        binding.addDomainBtn.setOnClickListener { attachCustomDomain() }

        // 自定义域：点击删除（带确认）
        binding.domainListView.setOnItemClickListener { _, _, position, _ ->
            val d = domainList.getOrNull(position) ?: return@setOnItemClickListener
            AlertDialog.Builder(this)
                .setTitle("删除自定义域")
                .setMessage("确定删除：${d.hostname} ？")
                .setPositiveButton("删除") { _, _ -> deleteCustomDomain(d) }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    // ------------------ Worker 列表（原逻辑不动） ------------------
    private fun loadWorkerList(account: Account) {
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/workers/scripts"
        val request = Request.Builder()
            .url(url).get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

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
                } catch (_: Exception) {
                    runOnUiThread { showToast("解析 Worker 列表失败") }
                    return
                }

                runOnUiThread {
                    workerList.clear()
                    workerList.addAll(names)
                    val adapter = ArrayAdapter(
                        this@RouteBindActivity,
                        android.R.layout.simple_spinner_item,
                        names
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.workerSpinner.adapter = adapter
                }
            }
        })
    }

    // ------------------ 自定义域：加载当前 Worker 的域名列表 ------------------
    private fun loadDomainsForSelectedWorker() {
        val acc = currentAccount ?: return
        val worker = binding.workerSpinner.selectedItem?.toString()?.trim().orEmpty()
        if (worker.isEmpty()) return

        val url = "https://api.cloudflare.com/client/v4/accounts/${acc.accountId}/workers/domains"
        val req = Request.Builder()
            .url(url).get()
            .addHeader("Authorization", "Bearer ${acc.token}")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { appendResult("获取自定义域失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread { appendResult("获取自定义域失败：$res") }
                    return
                }

                val list = mutableListOf<WorkerDomain>()
                try {
                    val root = JSONObject(res)
                    val arr = root.getJSONArray("result")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val service = o.optString("service", "")
                        if (service != worker) continue
                        list.add(
                            WorkerDomain(
                                id = o.getString("id"),
                                hostname = o.getString("hostname"),
                                service = service,
                                zoneId = o.optString("zone_id", null)
                            )
                        )
                    }
                } catch (e: Exception) {
                    runOnUiThread { appendResult("解析自定义域失败：${e.message}") }
                    return
                }

                runOnUiThread {
                    domainList.clear()
                    domainList.addAll(list)
                    refreshDomainListUI()
                }
            }
        })
    }

    private fun refreshDomainListUI() {
        val display = domainList.map { it.hostname }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)
        binding.domainListView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    // ------------------ 自定义域：绑定（Attach To Domain） ------------------
    private fun attachCustomDomain() {
        val acc = currentAccount ?: return
        val worker = binding.workerSpinner.selectedItem?.toString()?.trim().orEmpty()
        val host = binding.domainEdit.text.toString().trim()

        if (worker.isEmpty()) {
            showToast("请先选择 Worker")
            return
        }
        if (host.isEmpty()) {
            showToast("请输入域名")
            return
        }
        if (acc.zoneId.isNullOrEmpty()) {
            showToast("缺少 Zone ID，无法绑定自定义域")
            return
        }

        val url = "https://api.cloudflare.com/client/v4/accounts/${acc.accountId}/workers/domains"
        val json = JSONObject().apply {
            put("hostname", host)
            put("service", worker)
            put("zone_id", acc.zoneId)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())

        // 官方是 PUT attach 1
        val req = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Authorization", "Bearer ${acc.token}")
            .addHeader("Content-Type", "application/json")
            .build()

        binding.addDomainBtn.isEnabled = false
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.addDomainBtn.isEnabled = true
                    appendResult("绑定自定义域失败：${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string().orEmpty()
                runOnUiThread {
                    binding.addDomainBtn.isEnabled = true
                    if (response.isSuccessful) {
                        appendResult("自定义域绑定成功：$host")
                        binding.domainEdit.setText("")
                        loadDomainsForSelectedWorker()
                    } else {
                        appendResult("自定义域绑定失败：$res")
                    }
                }
            }
        })
    }

    // ------------------ 自定义域：删除（Detach From Domain） ------------------
    private fun deleteCustomDomain(d: WorkerDomain) {
        val acc = currentAccount ?: return

        val url = "https://api.cloudflare.com/client/v4/accounts/${acc.accountId}/workers/domains/${d.id}"
        val req = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${acc.token}")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { appendResult("删除自定义域失败：${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string().orEmpty()
                runOnUiThread {
                    if (response.isSuccessful) {
                        appendResult("删除自定义域成功：${d.hostname}")
                        loadDomainsForSelectedWorker()
                    } else {
                        appendResult("删除自定义域失败：$res")
                    }
                }
            }
        })
    }

    // ------------------ 原有脚本管理（不动） ------------------
    private fun showManageScriptsDialog() {
        val selectedItems = BooleanArray(workerList.size)
        AlertDialog.Builder(this)
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
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()

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

    private fun checkDeleteComplete(successCount: Int, failCount: Int, total: Int) {
        if (successCount + failCount >= total) {
            runOnUiThread {
                showToast("脚本删除完成（成功：$successCount，失败：$failCount）")
                currentAccount?.let { loadWorkerList(it) }
            }
        }
    }

    // ------------------ 你原有路由代码（保持不变） ------------------
    private fun loadCurrentRoutes(account: Account) {
        val url = "https://api.cloudflare.com/client/v4/zones/${account.zoneId}/workers/routes"
        val request = Request.Builder()
            .url(url).get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

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
        val patterns = binding.routesEdit.text.toString()
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (script.isEmpty() || patterns.isEmpty()) {
            showToast("请填写 Worker 名称 和 路由列表")
            return
        }

        binding.bindBtn.isEnabled = false
        binding.resultText.setTextSafe("正在绑定...\n")

        patterns.forEach { pattern ->
            val url = "https://api.cloudflare.com/client/v4/zones/$zoneId/workers/routes"
            val json = JSONObject().apply {
                put("pattern", pattern)
                put("script", script)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

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
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

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
        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/json")
            .build()

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

/**
 * 极简 Spinner 选择监听，避免写一堆空实现
 */
private class SimpleItemSelectedListener(
    val onSelected: () -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: android.view.View?,
        position: Int,
        id: Long
    ) = onSelected()

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
}