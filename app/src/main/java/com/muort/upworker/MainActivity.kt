package com.muort.upworker

import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
import com.muort.upworker.core.util.DisplaySizeHelper
import com.muort.upworker.core.util.MigrationResult
import com.muort.upworker.core.util.ThemeHelper
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 setContentView 之前应用动态配色
        ThemeHelper.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        configureSystemBars()
        setupAccountSelector()
        observeViewModel()
        performMigrationIfNeeded()
    }
    
    private fun configureSystemBars() {
        // 1. 获取 MD3 颜色定义
        val typedValueContainer = TypedValue()
        // Surface Container (用于 Toolbar 背景)
        val hasContainer = theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValueContainer, true)
        val colorSurfaceContainer = if (hasContainer) typedValueContainer.data else {
            val typedValueSurface = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValueSurface, true)
            typedValueSurface.data
        }
        
        // Secondary Container (用于账号选择器背景 - 胶囊样式)
        val typedValueSecContainer = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValueSecContainer, true)
        val colorSecondaryContainer = typedValueSecContainer.data
        
        // On Secondary Container (用于账号选择器前景)
        val typedValueOnSecContainer = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValueOnSecContainer, true)
        val colorOnSecondaryContainer = typedValueOnSecContainer.data

        // On Surface (用于 Toolbar 上的通用图标)
        val typedValueOnSurface = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValueOnSurface, true)
        val colorOnSurface = typedValueOnSurface.data

        // 2. 设置状态栏为透明，但不让内容延伸到状态栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 使用 WindowInsetsControllerCompat 设置状态栏颜色和模式
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        // 状态栏颜色设置依然保留，兼容旧设备
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // 3. 处理系统栏 insets，为内容添加 padding
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 4. Toolbar 样式调整：设置背景色、去阴影、居中
        binding.toolbar.setBackgroundColor(colorSurfaceContainer)
        binding.toolbar.setTitleTextColor(colorOnSurface)
        (binding.toolbar as? com.google.android.material.appbar.MaterialToolbar)?.isTitleCentered = true
        binding.toolbar.elevation = 0f
        
        // 5. 打造 "胶囊" (Chip) 样式的账号选择器
        // 创建圆角背景
        val chipBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(colorSecondaryContainer)
            cornerRadius = 100f // 大圆角
        }
        binding.selectAccountButton.background = chipBackground
        
        // 增加内边距 (8dp vertical, 16dp horizontal)
        val density = resources.displayMetrics.density
        val paddingH = (16 * density).toInt()
        val paddingV = (6 * density).toInt()
        binding.selectAccountButton.setPadding(paddingH, paddingV, paddingH, paddingV)
        
        // 6. 账号名称文字样式 - 使用主题属性以跟随动态配色
        binding.currentAccountText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        binding.currentAccountText.isSingleLine = false
        binding.currentAccountText.maxLines = 2
        binding.currentAccountText.ellipsize = android.text.TextUtils.TruncateAt.END
        
        // 选择器按钮内容颜色 (OnSecondaryContainer)
        val contentColorFilter = PorterDuffColorFilter(colorOnSecondaryContainer, PorterDuff.Mode.SRC_IN)
        
        (binding.selectAccountButton as? android.widget.TextView)?.let { textView ->
            textView.text = ""
            val icon = getDrawable(android.R.drawable.ic_menu_more)
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null)
            textView.setTextColor(colorOnSecondaryContainer)
            textView.compoundDrawables.forEach { it?.mutate()?.colorFilter = contentColorFilter }
            textView.compoundDrawablesRelative.forEach { it?.mutate()?.colorFilter = contentColorFilter }
        }
        
        // 7. 设置 Toolbar 导航图标颜色 (OnSurface)
        val navColorFilter = PorterDuffColorFilter(colorOnSurface, PorterDuff.Mode.SRC_IN)
        binding.toolbar.navigationIcon?.mutate()?.colorFilter = navColorFilter
        binding.toolbar.overflowIcon?.mutate()?.colorFilter = navColorFilter

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == 
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
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
                    showToast("已切换到 ${account.name}")
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
        
        binding.accountsButton.setOnClickListener {
            navController.navigate(R.id.accountListFragment)
        }
        
        binding.currentAccountText.setOnClickListener {
            val accountId = accountViewModel.defaultAccount.value?.id ?: -1L
            if (accountId != -1L) {
                navController.navigate(
                    R.id.accountEditFragment,
                    android.os.Bundle().apply { putLong("accountId", accountId) }
                )
            } else {
                navController.navigate(R.id.accountListFragment)
            }
        }
        
        // 监听导航变化，确保返回按钮/菜单图标颜色在页面切换后依然正确
        navController.addOnDestinationChangedListener { _, _, _ ->
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            val colorOnSurface = typedValue.data
            binding.toolbar.navigationIcon?.mutate()?.colorFilter = PorterDuffColorFilter(colorOnSurface, PorterDuff.Mode.SRC_IN)
        }
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
                        updateTitleBar(account)
                        account?.let {
                            accountViewModel.loadZonesForAccount(it.id)
                            // 仅应用冷启动时自动同步一次云端 zone 列表，避免每次返回主界面重复请求
                            accountViewModel.maybeFetchZonesOnColdStart(it)
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
    
    private fun updateTitleBar(account: Account?) {
        if (account == null) {
            binding.currentAccountText.text = "未选择账号"
        } else {
            binding.currentAccountText.text = account.name
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
