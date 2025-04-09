package com.muort.upworker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.muort.upworker.databinding.ActivityAccountManagerBinding

class AccountManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountManagerBinding
    private lateinit var adapter: ArrayAdapter<String>
    private val accounts = mutableListOf<Account>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载账号列表
        accounts.addAll(AccountStorage.loadAccounts(this))
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, accounts.map { it.name })
        binding.accountListView.adapter = adapter

        // 添加账号按钮
        binding.addAccountBtn.setOnClickListener {
            showAccountDialog(null)
        }

        // 列表项点击事件，编辑账号
        binding.accountListView.setOnItemClickListener { _, _, position, _ ->
            showAccountDialog(accounts[position], position)
        }

        // 长按删除账号
        binding.accountListView.setOnItemLongClickListener { _, _, position, _ ->
            AlertDialog.Builder(this)
                .setTitle("删除账号")
                .setMessage("确定要删除 ${accounts[position].name} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    accounts.removeAt(position)
                    saveAndRefresh()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        // 点击跳转到备份界面
        binding.backupBtn.setOnClickListener {
            val intent = Intent(this, BackupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAccountDialog(account: Account?, editIndex: Int? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_input, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.nameEdit)
        val accountIdEdit = dialogView.findViewById<EditText>(R.id.accountIdEdit)
        val tokenEdit = dialogView.findViewById<EditText>(R.id.tokenEdit)
        val zoneIdEdit = dialogView.findViewById<EditText>(R.id.zoneIdEdit) // 新增 Zone ID 输入框

        if (account != null) {
            nameEdit.setText(account.name)
            accountIdEdit.setText(account.accountId)
            tokenEdit.setText(account.token)
            zoneIdEdit.setText(account.zoneId)
        }

        AlertDialog.Builder(this)
            .setTitle(if (account == null) "添加账号" else "编辑账号")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val id = accountIdEdit.text.toString().trim()
                val token = tokenEdit.text.toString().trim()
                val zoneId = zoneIdEdit.text.toString().trim()

                if (name.isEmpty() || id.isEmpty() || token.isEmpty() || zoneId.isEmpty()) {
                    Toast.makeText(this, "请完整填写所有字段", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newAccount = Account(name, id, token, zoneId)
                if (editIndex != null) {
                    accounts[editIndex] = newAccount
                } else {
                    accounts.add(newAccount)
                }
                saveAndRefresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAndRefresh() {
        AccountStorage.saveAccounts(this, accounts)
        adapter.clear()
        adapter.addAll(accounts.map { it.name })
        adapter.notifyDataSetChanged()
    }
}