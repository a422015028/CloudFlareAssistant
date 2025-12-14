package com.muort.upworker

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.util.DataMigrationHelper
import com.muort.upworker.core.util.MigrationResult
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.ActivityMainBinding
import com.muort.upworker.databinding.DialogAccountSelectionBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val accountViewModel: AccountViewModel by viewModels()
    
    @Inject
    lateinit var migrationHelper: DataMigrationHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        setupAccountSelector()
        observeViewModel()
        performMigrationIfNeeded()
    }
    
    private fun setupAccountSelector() {
        binding.selectAccountButton.setOnClickListener {
            showAccountSelectionDialog()
        }
    }
    
    private fun showAccountSelectionDialog() {
        lifecycleScope.launch {
            val accounts = accountViewModel.accounts.value
            if (accounts.isEmpty()) {
                showToast("没有可用账号，请先添加账号")
                return@launch
            }
            
            val currentAccount = accountViewModel.defaultAccount.value
            
            // Create custom dialog
            val dialogBinding = DialogAccountSelectionBinding.inflate(LayoutInflater.from(this@MainActivity))
            val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setView(dialogBinding.root)
                .create()
            
            // Setup RecyclerView
            val adapter = AccountSelectionAdapter(
                accounts = accounts,
                currentAccountId = currentAccount?.id,
                onAccountSelected = { account ->
                    accountViewModel.setDefaultAccount(account.id)
                    dialog.dismiss()
                    showToast("已切换到账号: ${account.name}")
                }
            )
            
            dialogBinding.accountRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                this.adapter = adapter
            }
            
            dialogBinding.cancelButton.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        }
    }
    
    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment)
        )
        
        setupActionBarWithNavController(navController, appBarConfiguration)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        account?.let {
                            binding.currentAccountText.text = it.name
                        } ?: run {
                            binding.currentAccountText.text = "未选择账号"
                        }
                    }
                }
                
                launch {
                    accountViewModel.message.collect { message ->
                        showToast(message)
                    }
                }
            }
        }
    }
    
    private fun performMigrationIfNeeded() {
        lifecycleScope.launch {
            when (val result = migrationHelper.migrateDataIfNeeded()) {
                is MigrationResult.Success -> {
                    if (result.migratedCount > 0) {
                        showToast("已成功迁移 ${result.migratedCount} 个账号")
                        Timber.i("Successfully migrated ${result.migratedCount} accounts")
                    }
                }
                is MigrationResult.Failed -> {
                    showToast("数据迁移失败: ${result.error}")
                    Timber.e("Migration failed: ${result.error}")
                }
                is MigrationResult.AlreadyCompleted -> {
                    Timber.d("Migration already completed")
                }
            }
        }
    }
}
