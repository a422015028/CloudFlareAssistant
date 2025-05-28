package com.muort.upworker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.text.Editable
import android.widget.EditText

fun EditText.setTextSafe(text: String) {
    this.text = Editable.Factory.getInstance().newEditable(text)
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()
    private var selectedFile: File? = null
    private var currentAccount: Account? = null

    private val prefs by lazy {
        getSharedPreferences("upworker_prefs", MODE_PRIVATE)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val uri = it.data?.data
            uri?.let { pickedUri ->
                val fileName = pickedUri.lastPathSegment?.substringAfterLast("/") ?: "selected.js"
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(pickedUri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                selectedFile = tempFile
                binding.filePathEdit.setText(tempFile.absolutePath)
                val workerName = Regex("""(.+?)(?:-Worker|\.worker)?\.js""", RegexOption.IGNORE_CASE)
                    .find(fileName)?.groupValues?.get(1)
                workerName?.let { binding.workerNameEdit.setText(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.responseText.movementMethod = ScrollingMovementMethod.getInstance()

        // 恢复上次输入
        binding.workerNameEdit.setText(prefs.getString("last_worker_name", ""))
        binding.filePathEdit.setText(prefs.getString("last_file_path", ""))

        binding.selectFileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/javascript", "text/javascript", "text/plain"))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }

        binding.uploadBtn.setOnClickListener {
            val workerName = binding.workerNameEdit.text.toString().trim()
            val file = selectedFile ?: File(binding.filePathEdit.text.toString())
            if (workerName.isEmpty() || !file.exists() || !file.canRead()) {
                showToast("请填写有效的 Worker 名称和文件路径")
                return@setOnClickListener
            }
            val account = currentAccount
            if (account == null) {
                showToast("请先选择账号")
                return@setOnClickListener
            }

            // 保存历史
            prefs.edit()
                .putString("last_worker_name", workerName)
                .putString("last_file_path", file.absolutePath)
                .apply()

            binding.uploadBtn.isEnabled = false
            uploadToCloudflare(workerName, file, account)
        }

        binding.manageAccountsBtn.setOnClickListener {
            startActivity(Intent(this, AccountManagerActivity::class.java))
        }

        // 清除历史记录
        binding.clearHistoryBtn.setOnClickListener {
            prefs.edit()
                .remove("last_worker_name")
                .remove("last_file_path")
                .remove("last_selected_account")
                .apply()

            binding.workerNameEdit.setText("")
            binding.filePathEdit.setText("")
            binding.accountSpinner.setSelection(0)

            showToast("历史已清除")
        }

        setupAccountSpinner()
        setupBindRouteButton()

        // 新增按钮 - 启动 DNS 管理页面
        binding.manageDnsBtn.setOnClickListener {
            val account = currentAccount
            if (account == null) {
                showToast("请先选择账号")
                return@setOnClickListener
            }

            val intent = Intent(this, DnsManagerActivity::class.java).apply {
                putExtra("account_name", account.name)
                putExtra("account_id", account.accountId)
                putExtra("token", account.token)
                putExtra("zone_id", account.zoneId ?: "")
            }
            startActivity(intent)
        }
        // 新增按钮 - 启动 KV 管理页面
        binding.manageKvBtn.setOnClickListener {
            val account = currentAccount
            if (account == null) {
                showToast("请先选择账号")
                return@setOnClickListener
            }

            val intent = Intent(this, KvManagerActivity::class.java).apply {
                putExtra("account_name", account.name)
                putExtra("account_id", account.accountId)
                putExtra("token", account.token)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        setupAccountSpinner()
    }

    private fun setupBindRouteButton() {
        binding.bindRouteBtn.setOnClickListener {
            val account = currentAccount
            if (account == null) {
                showToast("请先选择账号")
                return@setOnClickListener
            }

            val intent = Intent(this, RouteBindActivity::class.java).apply {
                putExtra("account_name", account.name)
                putExtra("account_id", account.accountId)
                putExtra("token", account.token)
                putExtra("zone_id", account.zoneId ?: "")
            }
            startActivity(intent)
        }
    }

    private fun setupAccountSpinner() {
        val accounts = AccountStorage.loadAccounts(this)
        Log.d("UPWORKER", "加载到账号数: ${accounts.size}")
        if (accounts.isEmpty()) {
            binding.accountSpinner.adapter = null
            currentAccount = null
            return
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accounts.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.accountSpinner.adapter = adapter

        val lastSelected = prefs.getString("last_selected_account", null)
        val selectedIndex = accounts.indexOfFirst { it.name == lastSelected }.takeIf { it != -1 } ?: 0
        currentAccount = accounts[selectedIndex]
        binding.accountSpinner.setSelection(selectedIndex)

        binding.accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                currentAccount = accounts[position]
                prefs.edit().putString("last_selected_account", currentAccount?.name).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun uploadToCloudflare(workerName: String, file: File, account: Account) {
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/workers/scripts/$workerName"

        val request = Request.Builder()
            .url(url)
            .put(file.asRequestBody("application/javascript".toMediaType()))
            .addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/javascript")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                   // binding.responseText.text = "上传失败: ${e.message}"
                    binding.responseText.setTextSafe("上传失败: ${e.message}")
                    showToast("上传失败")
                    binding.uploadBtn.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                  //  binding.responseText.text = "上传成功，状态码: ${response.code}\n返回: $body"
                    binding.responseText.setTextSafe("上传成功，状态码: ${response.code}\n返回: $body")
                    showToast("上传成功")
                    binding.uploadBtn.isEnabled = true
                }
            }
        })
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}