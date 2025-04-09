package com.muort.upworker

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityRouteBindBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.widget.ArrayAdapter

class RouteBindActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRouteBindBinding
    private val client = OkHttpClient()
    private var currentAccount: Account? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteBindBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resultText.movementMethod = ScrollingMovementMethod.getInstance()

        // 从 Intent 获取账号数据
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
        currentAccount?.let { loadWorkerList(it) }
        binding.bindBtn.setOnClickListener { bindRoutes() }
    }


    private fun loadWorkerList(account: Account) {
    val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/workers/scripts"
    val request = Request.Builder()
        .url(url)
        .get()
        .addHeader("Authorization", "Bearer ${account.token}")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            runOnUiThread {
                showToast("获取 Worker 列表失败：${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val json = response.body?.string() ?: return
            val names = mutableListOf<String>()
            try {
                val root = JSONObject(json)
                val result = root.getJSONArray("result")
                for (i in 0 until result.length()) {
                    val item = result.getJSONObject(i)
                    names.add(item.getString("id"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("解析 Worker 列表失败")
                }
                return
            }

            runOnUiThread {
                val adapter = ArrayAdapter(this@RouteBindActivity, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.workerSpinner.adapter = adapter
            }
        }
    })
}
    private fun bindRoutes() {
        val account = currentAccount ?: return
        val zoneId = account.zoneId
        val token = account.token
        val script = binding.workerSpinner.selectedItem?.toString()?.trim() ?: ""
        val patterns = binding.routesEdit.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (script.isEmpty() || zoneId.isNullOrEmpty() || token.isEmpty() || patterns.isEmpty()) {
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
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        appendResult("失败：$pattern\n错误：${e.message}\n")
                    }
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

    private fun appendResult(msg: String) {
        binding.resultText.append(msg + "\n")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}