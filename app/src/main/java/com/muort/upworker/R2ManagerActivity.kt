package com.muort.upworker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityR2ManagerBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class R2ManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityR2ManagerBinding
    private val client = OkHttpClient()
    private var currentAccount: Account? = null
    private val buckets = mutableListOf<String>()
    private var selectedBucket: String? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            val uri = it.data?.data
            uri?.let { pickedUri ->
                val fileName = pickedUri.lastPathSegment?.substringAfterLast("/") ?: "selected_file"
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(pickedUri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                uploadObject(tempFile)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityR2ManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("account_name")
        val accountId = intent.getStringExtra("account_id")
        val token = intent.getStringExtra("token")

        if (name == null || accountId == null || token == null) {
            Toast.makeText(this, "账号信息无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentAccount = Account(name, accountId, token, null)

        binding.refreshBtn.setOnClickListener { loadBuckets() }
        binding.createBucketBtn.setOnClickListener { createBucket() }
        binding.deleteBucketBtn.setOnClickListener { deleteBucket() }
        binding.listObjectsBtn.setOnClickListener { listObjects() }
        binding.uploadBtn.setOnClickListener { selectFileForUpload() }
        binding.downloadBtn.setOnClickListener { downloadObject() }
        binding.deleteObjectBtn.setOnClickListener { deleteObject() }
        binding.bucketsList.setOnItemClickListener { _, _, position, _ ->
            selectedBucket = buckets[position]
            binding.selectedBucketText.text = "当前选择: $selectedBucket"
        }

        binding.bucketsList.setOnItemLongClickListener { _, _, position, _ ->
            val bucketName = buckets[position]
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除桶")
                .setMessage("确定删除桶 $bucketName 吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    deleteBucketByName(bucketName)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        loadBuckets()
    }

    private fun loadBuckets() {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("网络错误: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: run {
                    runOnUiThread { toast("API响应为空") }
                    return
                }

                runOnUiThread {
                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            val json = JSONObject(res)
                            val errors = json.optJSONArray("errors")
                            if (errors != null && errors.length() > 0) {
                                errors.getJSONObject(0).optString("message", res)
                            } else {
                                json.optString("errors", res)
                            }
                        } catch (e: Exception) {
                            res
                        }
                        toast("加载桶列表失败 (${response.code}): $errorMsg")
                        return@runOnUiThread
                    }

                    try {
                        val json = JSONObject(res)
                        if (!json.has("result")) {
                            toast("API响应格式错误: 缺少result字段")
                            return@runOnUiThread
                        }
                        // R2 API返回的是 result.buckets 结构
                        val resultObj = json.getJSONObject("result")
                        if (!resultObj.has("buckets")) {
                            toast("API响应格式错误: 缺少buckets字段")
                            return@runOnUiThread
                        }
                        val bucketsArray = resultObj.getJSONArray("buckets")
                        buckets.clear()
                        for (i in 0 until bucketsArray.length()) {
                            val bucket = bucketsArray.getJSONObject(i)
                            val name = bucket.optString("name", "")
                            if (name.isNotEmpty()) {
                                buckets.add(name)
                            }
                        }
                        val adapter = ArrayAdapter(this@R2ManagerActivity, android.R.layout.simple_list_item_1, buckets)
                        binding.bucketsList.adapter = adapter
                        if (buckets.isNotEmpty()) {
                            selectedBucket = buckets[0]
                            binding.selectedBucketText.text = "当前选择: $selectedBucket"
                        } else {
                            selectedBucket = null
                            binding.selectedBucketText.text = "未选择桶"
                        }
                        toast("加载完成，共${buckets.size}个桶")
                    } catch (e: Exception) {
                        toast("解析失败: ${e.message}")
                    }
                }
            }
        })
    }

    private fun createBucket() {
        val bucketName = binding.bucketNameEdit.text.toString().trim()
        if (bucketName.isEmpty()) {
            toast("请输入桶名称")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets"
        val json = JSONObject().apply {
            put("name", bucketName)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer ${account.token}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("网络错误: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("创建成功")
                        binding.bucketNameEdit.setText("")
                        loadBuckets()
                    } else {
                        val errorMsg = try {
                            val errorJson = JSONObject(res)
                            errorJson.optString("errors", res)
                        } catch (e: Exception) {
                            res
                        }
                        toast("创建失败 ${response.code}: $errorMsg")
                    }
                }
            }
        })
    }

    private fun deleteBucket() {
        val bucketName = selectedBucket ?: binding.bucketNameEdit.text.toString().trim()
        if (bucketName.isEmpty()) {
            toast("请选择或输入要删除的桶")
            return
        }
        deleteBucketByName(bucketName)
    }

    private fun deleteBucketByName(bucketName: String) {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets/$bucketName"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("网络错误: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("删除成功")
                        binding.bucketNameEdit.setText("")
                        selectedBucket = null
                        loadBuckets()
                    } else {
                        val errorMsg = when (response.code) {
                            409 -> "删除失败：桶不为空\n\n请先删除桶内的所有对象后再删除桶。\n\n操作步骤：\n1. 选择该桶\n2. 点击\"列出对象\"\n3. 逐个删除对象\n4. 再删除桶"
                            403 -> "删除失败：权限不足\n\n请检查API Token是否有删除权限"
                            404 -> "删除失败：桶不存在"
                            else -> {
                                try {
                                    val json = JSONObject(res)
                                    val errors = json.optJSONArray("errors")
                                    if (errors != null && errors.length() > 0) {
                                        "删除失败 ${response.code}: ${errors.getJSONObject(0).optString("message", res)}"
                                    } else {
                                        "删除失败 ${response.code}: $res"
                                    }
                                } catch (e: Exception) {
                                    "删除失败 ${response.code}: $res"
                                }
                            }
                        }
                        toast(errorMsg)
                    }
                }
            }
        })
    }

    private fun listObjects() {
        val bucket = selectedBucket
        if (bucket == null) {
            toast("请先选择桶")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets/$bucket/objects"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("获取对象列表失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                runOnUiThread {
                    try {
                        val json = JSONObject(res)
                        val result = json.getJSONArray("result")
                        val objects = StringBuilder()
                        for (i in 0 until result.length()) {
                            val obj = result.getJSONObject(i)
                            val key = obj.getString("key")
                            val size = obj.optLong("size", 0)
                            val uploaded = obj.optString("uploaded", "未知")
                            objects.append("$key (${size} bytes, $uploaded)\n")
                        }
                        if (objects.isEmpty()) {
                            objects.append("桶为空")
                        }

                        val objectList = mutableListOf<String>()
                        for (i in 0 until result.length()) {
                            val obj = result.getJSONObject(i)
                            objectList.add(obj.getString("key"))
                        }

                        if (objectList.isEmpty()) {
                            androidx.appcompat.app.AlertDialog.Builder(this@R2ManagerActivity)
                                .setTitle("对象列表 - $bucket")
                                .setMessage("桶为空")
                                .setPositiveButton("确定", null)
                                .show()
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder(this@R2ManagerActivity)
                                .setTitle("对象列表 - $bucket (点击复制)")
                                .setItems(objectList.toTypedArray()) { _, which ->
                                    val selectedKey = objectList[which]
                                    binding.objectNameEdit.setText(selectedKey)
                                    toast("已复制: $selectedKey")
                                }
                                .setNegativeButton("关闭", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        toast("解析对象列表失败")
                    }
                }
            }
        })
    }

    private fun selectFileForUpload() {
        if (selectedBucket == null) {
            toast("请先选择桶")
            return
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun uploadObject(file: File) {
        val bucket = selectedBucket ?: return
        val objectName = binding.objectNameEdit.text.toString().trim()
        if (objectName.isEmpty()) {
            toast("请输入对象名称")
            return
        }

        val account = currentAccount ?: return
        val encodedObjectName = java.net.URLEncoder.encode(objectName, "UTF-8").replace("+", "%20")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets/$bucket/objects/$encodedObjectName"
        val body = file.asRequestBody("*/*".toMediaType())

        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("上传失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("上传成功")
                    } else {
                        toast("上传失败")
                    }
                }
            }
        })
    }

    private fun downloadObject() {
        val bucket = selectedBucket ?: return
        val objectName = binding.objectNameEdit.text.toString().trim()
        if (objectName.isEmpty()) {
            toast("请输入对象名称")
            return
        }

        val account = currentAccount ?: return
        val encodedObjectName = java.net.URLEncoder.encode(objectName, "UTF-8").replace("+", "%20")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets/$bucket/objects/$encodedObjectName"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("网络错误: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        // 从路径中提取文件名
                        val fileName = objectName.substringAfterLast('/')
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        )
                        val file = File(downloadsDir, fileName)
                        
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(file).use { output -> input.copyTo(output) }
                        }
                        runOnUiThread { 
                            toast("下载成功: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("保存文件失败: ${e.message}") }
                    }
                } else {
                    val errorMsg = response.body?.string() ?: "未知错误"
                    runOnUiThread { toast("下载失败 (${response.code}): $errorMsg") }
                }
            }
        })
    }

    private fun deleteObject() {
        val bucket = selectedBucket
        if (bucket == null) {
            toast("请先选择桶")
            return
        }
        
        val objectName = binding.objectNameEdit.text.toString().trim()
        if (objectName.isEmpty()) {
            toast("请输入对象名称")
            return
        }

        // 确认对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除对象")
            .setMessage("确定删除对象 \"$objectName\" 吗？\n此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                performDeleteObject(bucket, objectName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performDeleteObject(bucket: String, objectName: String) {
        val account = currentAccount ?: return
        val encodedObjectName = java.net.URLEncoder.encode(objectName, "UTF-8").replace("+", "%20")
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/r2/buckets/$bucket/objects/$encodedObjectName"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("网络错误: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                runOnUiThread {
                    when (response.code) {
                        204, 200 -> {
                            // R2 DELETE返回204或200表示成功
                            toast("删除成功")
                            binding.objectNameEdit.setText("")
                        }
                        404 -> {
                            toast("删除失败：对象不存在\n\n对象名称: $objectName\n\n提示：请确认对象名称是否正确")
                        }
                        403 -> {
                            toast("删除失败：权限不足")
                        }
                        else -> {
                            val errorMsg = try {
                                val json = JSONObject(res)
                                val errors = json.optJSONArray("errors")
                                if (errors != null && errors.length() > 0) {
                                    errors.getJSONObject(0).optString("message", "未知错误")
                                } else {
                                    "未知错误"
                                }
                            } catch (e: Exception) {
                                res
                            }
                            toast("删除失败 ${response.code}: $errorMsg")
                        }
                    }
                }
            }
        })
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}