package com.muort.upworker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebDavConfigActivity : AppCompatActivity() {

    private lateinit var urlEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webdav_config)

        urlEditText = findViewById(R.id.urlEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        saveButton = findViewById(R.id.saveButton)

        // 加载现有配置
        loadWebDavConfig()

        // 保存按钮点击事件
        saveButton.setOnClickListener {
            val url = urlEditText.text.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            // 保存 WebDAV 配置
            WebDavConfig.save(this, url, username, password)

            Toast.makeText(this, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    // 加载 WebDAV 配置到输入框
    private fun loadWebDavConfig() {
        val (url, username, password) = WebDavConfig.load(this)
        urlEditText.setText(url)
        usernameEditText.setText(username)
        passwordEditText.setText(password)
    }
}