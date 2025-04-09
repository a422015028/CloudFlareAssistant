package com.muort.upworker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class BackupActivity : AppCompatActivity() {

    private lateinit var exportBtn: Button
    private lateinit var importBtn: Button
    private lateinit var webDavConfigBtn: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        exportBtn = findViewById(R.id.exportBtn)
        importBtn = findViewById(R.id.importBtn)
        webDavConfigBtn = findViewById(R.id.webDavConfigBtn)
        statusText = findViewById(R.id.statusText)

        exportBtn.setOnClickListener {
            exportToWebDAV()
        }

        importBtn.setOnClickListener {
            importFromWebDAV()
        }

        webDavConfigBtn.setOnClickListener {
            startActivity(Intent(this, WebDavConfigActivity::class.java))
        }
    }

    // 从 SharedPreferences 中提取 accounts 字段生成备份文件
    private fun generateBackupFile(): File {
        val file = File(cacheDir, "accounts_backup.json")
        val prefs = getSharedPreferences("cloudflare_accounts", MODE_PRIVATE)
        val json = prefs.getString("accounts", null)

        if (json.isNullOrEmpty()) {
            file.writeText("[]")
        } else {
            file.writeText(json)
        }

        return file
    }

    private fun exportToWebDAV() {
        val file = generateBackupFile() // 生成本地备份文件

        WebDavUtils.uploadToWebDav(this, file) { success: Boolean, message: String ->
            runOnUiThread {
                if (success) {
                    statusText.text = "备份已上传到 WebDAV"
                    Toast.makeText(this, "备份成功", Toast.LENGTH_SHORT).show()
                } else {
                    statusText.text = "备份上传失败"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importFromWebDAV() {
        val fileName = "accounts_backup.json"

        WebDavUtils.downloadFromWebDav(this, fileName) { file: File? ->
            runOnUiThread {
                if (file != null && file.exists()) {
                    val restored = restoreFromBackupFile(file)
                    if (restored) {
                        statusText.text = "备份已恢复"
                        Toast.makeText(this, "备份已恢复", Toast.LENGTH_SHORT).show()
                    } else {
                        statusText.text = "恢复失败（格式错误）"
                        Toast.makeText(this, "恢复失败（格式错误）", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    statusText.text = "恢复失败"
                    Toast.makeText(this, "恢复失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 从 JSON 文件恢复数据写入 SharedPreferences
    private fun restoreFromBackupFile(file: File): Boolean {
        return try {
            val json = file.readText()
            if (json.startsWith("[")) {
                val prefs = getSharedPreferences("cloudflare_accounts", MODE_PRIVATE)
                prefs.edit().putString("accounts", json).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}