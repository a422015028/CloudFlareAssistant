package com.muort.upworker

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.util.DataMigrationHelper
import com.muort.upworker.core.util.MigrationResult
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.ActivityMainBinding
import com.muort.upworker.databinding.DialogAccountSelectionBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TARGET_DENSITY = 3.5f
        private const val TARGET_DENSITY_DPI = (160 * TARGET_DENSITY).toInt()
    }
    
    private lateinit var binding: ActivityMainBinding
    private val accountViewModel: AccountViewModel by viewModels()
    
    @Inject
    lateinit var migrationHelper: DataMigrationHelper
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(createConfigurationContext(newBase))
    }
    
    /**
     * 创建固定配置的Context
     */
    private fun createConfigurationContext(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.fontScale = 1.0f
        config.densityDpi = TARGET_DENSITY_DPI
        return context.createConfigurationContext(config)
    }
    
    override fun getResources(): Resources {
        val res = super.getResources()
        adaptDisplayDensity(res)
        return res
    }
    
    /**
     * 固定应用显示大小和字体大小，不跟随系统设置变化
     */
    @Suppress("DEPRECATION")
    private fun adaptDisplayDensity(res: Resources) {
        val config = res.configuration
        val dm = res.displayMetrics
        
        // 固定 fontScale 为 1.0
        if (config.fontScale != 1.0f) {
            config.fontScale = 1.0f
        }
        if (config.densityDpi != TARGET_DENSITY_DPI) {
            config.densityDpi = TARGET_DENSITY_DPI
        }
        
        // 设置固定的显示密度和字体密度
        dm.density = TARGET_DENSITY
        dm.densityDpi = TARGET_DENSITY_DPI
        dm.scaledDensity = TARGET_DENSITY
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
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
        
        // 6. 设置选择器内容颜色 (OnSecondaryContainer)
        val contentColorFilter = PorterDuffColorFilter(colorOnSecondaryContainer, PorterDuff.Mode.SRC_IN)
        
        binding.currentAccountText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        binding.currentAccountText.setTextColor(colorOnSecondaryContainer)
        binding.currentAccountText.isSingleLine = false
        binding.currentAccountText.maxLines = 2
        binding.currentAccountText.ellipsize = android.text.TextUtils.TruncateAt.END
        binding.currentAccountText.compoundDrawables.forEach { it?.mutate()?.colorFilter = contentColorFilter }
        binding.currentAccountText.compoundDrawablesRelative.forEach { it?.mutate()?.colorFilter = contentColorFilter }
        
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
                    showToast("已切换到账号: ${account.name}")
                    
                    // Load zones and show zone selection dialog
                    accountViewModel.loadZonesForAccount(account.id)
                    lifecycleScope.launch {
                        // Wait a bit for zones to load
                        kotlinx.coroutines.delay(300)
                        showZoneSelectionDialog(account)
                    }
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
                    combine(
                        accountViewModel.defaultAccount,
                        accountViewModel.selectedZone
                    ) { account, zone ->
                        Pair(account, zone)
                    }.collect { (account, zone) ->
                        updateTitleBar(account, zone)
                    }
                }                

                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        account?.let {
                            accountViewModel.loadZonesForAccount(it.id)
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
    
    private fun updateTitleBar(account: Account?, zone: Zone?) {
        if (account == null) {
            binding.currentAccountText.text = "未选择账号"
            return
        }

        val accountName = account.name
        val zoneName = zone?.name

        if (zoneName.isNullOrEmpty()) {
            binding.currentAccountText.text = accountName
        } else {
            val text = "$accountName\n$zoneName"
            val spannable = SpannableString(text)
            
            // 设置域名部分为正常字体（非粗体）和小字号 (75%)
            val start = accountName.length + 1
            val end = text.length
            
            spannable.setSpan(StyleSpan(android.graphics.Typeface.NORMAL), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            binding.currentAccountText.text = spannable
        }
    }
    
    private fun showZoneSelectionDialog(account: Account) {
        val zones = accountViewModel.zones.value
        val selectedZone = accountViewModel.selectedZone.value
        
        if (zones.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("选择域名")
                .setMessage("该账号暂无域名。\n\n您可以在账号管理页面通过API获取域名列表。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        val items = zones.map { zone ->
            val status = if (zone.status == "active") "✓" else "○"
            val selected = if (zone.id == selectedZone?.id) " [当前]" else ""
            "$status ${zone.name}$selected"
        }.toTypedArray()
        
        val selectedIndex = zones.indexOfFirst { it.id == selectedZone?.id }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("选择域名 - ${account.name}")
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                val zone = zones[which]
                accountViewModel.selectZone(account.id, zone.id)
                dialog.dismiss()
                showToast("已选择域名: ${zone.name}")
            }
            .setNeutralButton("从API刷新") { dialog, _ ->
                dialog.dismiss()
                accountViewModel.fetchZonesFromApi(account)
                lifecycleScope.launch {
                    // Wait for API call to complete
                    kotlinx.coroutines.delay(1500)
                    showZoneSelectionDialog(account)
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
