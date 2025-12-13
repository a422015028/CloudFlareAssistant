package com.muort.upworker

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityPagesManagerBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class PagesManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPagesManagerBinding
    private val client = OkHttpClient()
    private var currentAccount: Account? = null
    private val projects = mutableListOf<String>()
    private var selectedProject: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPagesManagerBinding.inflate(layoutInflater)
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

        binding.refreshBtn.setOnClickListener { loadProjects() }
        binding.createBtn.setOnClickListener { createProject() }
        binding.deployBtn.setOnClickListener { deployProject() }
        binding.addDomainBtn.setOnClickListener { addCustomDomain() }
        binding.listDomainsBtn.setOnClickListener { listCustomDomains() }
        binding.deleteDomainBtn.setOnClickListener { deleteCustomDomain() }

        binding.projectsList.setOnItemClickListener { _, _, position, _ ->
            selectedProject = projects[position]
            binding.selectedProjectText.text = "当前选择项目: $selectedProject"
            showProjectDetails(selectedProject!!)
        }

        binding.projectsList.setOnItemLongClickListener { _, _, position, _ ->
            val projectName = projects[position]
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除项目")
                .setMessage("确定删除项目 $projectName 吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    deleteProject(projectName)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        loadProjects()
    }

    private fun loadProjects() {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects"
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
                runOnUiThread {
                    try {
                        val json = JSONObject(res)
                        val result = json.getJSONArray("result")
                        projects.clear()
                        for (i in 0 until result.length()) {
                            val project = result.getJSONObject(i)
                            projects.add(project.getString("name"))
                        }
                        val adapter = ArrayAdapter(this@PagesManagerActivity, android.R.layout.simple_list_item_1, projects)
                        binding.projectsList.adapter = adapter
                        if (projects.isNotEmpty()) {
                            selectedProject = projects[0]
                            binding.selectedProjectText.text = "当前选择项目: $selectedProject"
                        } else {
                            selectedProject = null
                            binding.selectedProjectText.text = "未选择项目"
                        }
                        toast("加载完成，共${projects.size}个项目")
                    } catch (e: Exception) {
                        toast("解析失败")
                    }
                }
            }
        })
    }

    private fun createProject() {
        val projectName = binding.projectNameEdit.text.toString().trim()
        if (projectName.isEmpty()) {
            toast("请输入项目名称")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects"
        val json = JSONObject().apply {
            put("name", projectName)
            put("production_branch", "main")
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
                runOnUiThread { toast("创建失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("创建成功")
                        binding.projectNameEdit.setText("")
                        loadProjects()
                    } else {
                        toast("创建失败")
                    }
                }
            }
        })
    }

    private fun deleteProject(projectName: String) {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$projectName"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("删除失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("删除成功")
                        loadProjects()
                    } else {
                        toast("删除失败")
                    }
                }
            }
        })
    }

    private fun deployProject() {
        val project = selectedProject
        if (project == null) {
            toast("请先选择项目")
            return
        }

        val branch = binding.branchEdit.text.toString().trim()
        val branchToUse = if (branch.isEmpty()) "main" else branch

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$project/deployments"
        val json = JSONObject().apply {
            put("branch", branchToUse)
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
                runOnUiThread { toast("部署失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("部署成功")
                        binding.branchEdit.setText("")
                    } else {
                        toast("部署失败")
                    }
                }
            }
        })
    }

    private fun deleteCustomDomain() {
        val project = selectedProject
        if (project == null) {
            toast("请先选择项目")
            return
        }

        val domain = binding.deleteDomainEdit.text.toString().trim()
        if (domain.isEmpty()) {
            toast("请输入要删除的域名")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$project/domains/$domain"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("删除域名失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("域名删除成功")
                        binding.deleteDomainEdit.setText("")
                    } else {
                        toast("域名删除失败")
                    }
                }
            }
        })
    }

    private fun addCustomDomain() {
        val project = selectedProject
        if (project == null) {
            toast("请先选择项目")
            return
        }

        val domain = binding.domainEdit.text.toString().trim()
        if (domain.isEmpty()) {
            toast("请输入域名")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$project/domains"
        val json = JSONObject().apply {
            put("name", domain)
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
                runOnUiThread { toast("添加域名失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        toast("域名添加成功")
                        binding.domainEdit.setText("")
                    } else {
                        toast("域名添加失败")
                    }
                }
            }
        })
    }

    private fun listCustomDomains() {
        val project = selectedProject
        if (project == null) {
            toast("请先选择项目")
            return
        }

        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$project/domains"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("获取域名列表失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                runOnUiThread {
                    try {
                        val json = JSONObject(res)
                        val result = json.getJSONArray("result")
                        val domains = StringBuilder()
                        for (i in 0 until result.length()) {
                            val domainObj = result.getJSONObject(i)
                            val name = domainObj.getString("name")
                            val status = domainObj.optString("status", "unknown")
                            domains.append("$name (状态: $status)\n")
                        }
                        if (domains.isEmpty()) {
                            domains.append("无自定义域名")
                        }

                        androidx.appcompat.app.AlertDialog.Builder(this@PagesManagerActivity)
                            .setTitle("自定义域名列表")
                            .setMessage(domains.toString().trim())
                            .setPositiveButton("确定", null)
                            .show()
                    } catch (e: Exception) {
                        toast("解析域名列表失败")
                    }
                }
            }
        })
    }

    private fun showProjectDetails(projectName: String) {
        val account = currentAccount ?: return
        val url = "https://api.cloudflare.com/client/v4/accounts/${account.accountId}/pages/projects/$projectName"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${account.token}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("获取详情失败: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                runOnUiThread {
                    try {
                        val json = JSONObject(res)
                        val result = json.getJSONObject("result")
                        val name = result.getString("name")
                        val subdomain = result.optString("subdomain", "无")
                        val domains = result.optJSONArray("domains")?.join(", ") ?: "无"
                        val productionBranch = result.optString("production_branch", "无")
                        val createdOn = result.optString("created_on", "未知")

                        val details = """
                            项目名称: $name
                            子域名: $subdomain
                            自定义域名: $domains
                            生产分支: $productionBranch
                            创建时间: $createdOn
                        """.trimIndent()

                        androidx.appcompat.app.AlertDialog.Builder(this@PagesManagerActivity)
                            .setTitle("项目详情")
                            .setMessage(details)
                            .setPositiveButton("确定", null)
                            .show()
                    } catch (e: Exception) {
                        toast("解析详情失败")
                    }
                }
            }
        })
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}