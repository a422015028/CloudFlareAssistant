package com.muort.upworker

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityDnsManagerBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DnsManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDnsManagerBinding
    private val client = OkHttpClient()
    private var currentAccount: Account? = null
    private val dnsRecords = mutableListOf<DnsRecord>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("account_name")
        val accountId = intent.getStringExtra("account_id")
        val token = intent.getStringExtra("token")
        val zoneId = intent.getStringExtra("zone_id")

        if (name == null || accountId == null || token == null || zoneId == null) {
            Toast.makeText(this, "账号信息无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentAccount = Account(name, accountId, token, zoneId)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.dnsListView.adapter = adapter

        loadDnsRecords()

        binding.addDnsBtn.setOnClickListener {
            showDnsDialog(null)
        }

        binding.dnsListView.setOnItemClickListener { _, _, position, _ ->
            showDnsDialog(dnsRecords[position])
        }

        binding.dnsListView.setOnItemLongClickListener { _, _, position, _ ->
            val record = dnsRecords[position]
            AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除 ${record.name} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    deleteDnsRecord(record)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    private fun loadDnsRecords() {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/zones/${account.zoneId}/dns_records"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("加载失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                val resultList = mutableListOf<DnsRecord>()
                try {
                    val json = JSONObject(res)
                    val result = json.getJSONArray("result")
                    for (i in 0 until result.length()) {
                        val obj = result.getJSONObject(i)
                        resultList.add(
                            DnsRecord(
                                id = obj.getString("id"),
                                type = obj.getString("type"),
                                name = obj.getString("name"),
                                content = obj.getString("content"),
                                proxied = obj.optBoolean("proxied", false),
                                ttl = obj.optInt("ttl", 1)
                            )
                        )
                    }
                } catch (e: Exception) {
                    runOnUiThread { toast("解析失败") }
                    return
                }

                runOnUiThread {
                    dnsRecords.clear()
                    dnsRecords.addAll(resultList)
                    adapter.clear()
                    adapter.addAll(dnsRecords.map { "${it.type} ${it.name} -> ${it.content} (TTL=${it.ttl})" })
                }
            }
        })
    }

    private fun showDnsDialog(record: DnsRecord?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dns_input, null)
        val typeEdit = dialogView.findViewById<EditText>(R.id.typeEdit)
        val nameEdit = dialogView.findViewById<EditText>(R.id.nameEdit)
        val contentEdit = dialogView.findViewById<EditText>(R.id.contentEdit)
        val ttlEdit = dialogView.findViewById<EditText>(R.id.ttlEdit)
        val proxiedCheckBox = dialogView.findViewById<CheckBox>(R.id.proxiedCheckBox)

        record?.let {
            typeEdit.setText(it.type)
            nameEdit.setText(it.name)
            contentEdit.setText(it.content)
            ttlEdit.setText(it.ttl.toString())
            proxiedCheckBox.isChecked = it.proxied
        }

        AlertDialog.Builder(this)
            .setTitle(if (record == null) "添加记录" else "编辑记录")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val type = typeEdit.text.toString().trim()
                val name = nameEdit.text.toString().trim()
                val content = contentEdit.text.toString().trim()
                val ttl = ttlEdit.text.toString().toIntOrNull() ?: 1
                val proxied = proxiedCheckBox.isChecked

                if (type.isEmpty() || name.isEmpty() || content.isEmpty()) {
                    toast("请填写所有字段")
                    return@setPositiveButton
                }

                if (record == null) {
                    addDnsRecord(type, name, content, ttl, proxied)
                } else {
                    updateDnsRecord(record.copy(type = type, name = name, content = content, ttl = ttl, proxied = proxied))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addDnsRecord(type: String, name: String, content: String, ttl: Int, proxied: Boolean) {
        val account = currentAccount ?: return
        val json = JSONObject().apply {
            put("type", type)
            put("name", name)
            put("content", content)
            put("ttl", ttl)
            put("proxied", proxied)
        }

        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones/${account.zoneId}/dns_records")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(simpleCallback("添加") { loadDnsRecords() })
    }

    private fun updateDnsRecord(record: DnsRecord) {
        val account = currentAccount ?: return
        val json = JSONObject().apply {
            put("type", record.type)
            put("name", record.name)
            put("content", record.content)
            put("ttl", record.ttl)
            put("proxied", record.proxied)
        }

        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones/${account.zoneId}/dns_records/${record.id}")
            .put(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(simpleCallback("更新") { loadDnsRecords() })
    }

    private fun deleteDnsRecord(record: DnsRecord) {
        val account = currentAccount ?: return

        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/zones/${account.zoneId}/dns_records/${record.id}")
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(simpleCallback("删除") { loadDnsRecords() })
    }

    private fun simpleCallback(action: String, onSuccess: () -> Unit) = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            runOnUiThread { toast("$action 失败: ${e.message}") }
        }

        override fun onResponse(call: Call, response: Response) {
            runOnUiThread {
                if (response.isSuccessful) {
                    toast("$action 成功")
                    onSuccess()
                } else {
                    toast("$action 失败")
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}