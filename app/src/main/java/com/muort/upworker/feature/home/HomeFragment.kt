package com.muort.upworker.feature.home

import com.muort.upworker.core.log.LogRepository
import com.muort.upworker.core.util.LocaleHelper
import com.muort.upworker.core.util.ThemeHelper
import com.muort.upworker.core.util.safeNavigate
import kotlinx.coroutines.flow.collectLatest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.util.AnimationHelper
import com.muort.upworker.core.util.DisplaySizeHelper
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogAboutBinding
import com.muort.upworker.databinding.FragmentHomeBinding
import com.muort.upworker.feature.account.AccountViewModel
import com.muort.upworker.feature.dashboard.AccountAnalyticsState
import com.muort.upworker.feature.dashboard.AccountAnalyticsViewModel
import com.muort.upworker.feature.dashboard.DashboardState
import com.muort.upworker.feature.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.time.Year
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class HomeFragment : Fragment() {
        override fun onAttach(context: android.content.Context) {
            super.onAttach(context)
            LogRepository.init(context.applicationContext)
        }
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val accountAnalyticsViewModel: AccountAnalyticsViewModel by viewModels()
    
    // 用于在 Fragment 销毁时移除布局监听
    private var globalLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    
    // 缓存当前卡片布局方向，避免重复修改导致布局循环
    private var isCardHorizontal: Boolean? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
        setupAccountAnalytics()

        // 监听布局变化，动态调整卡片方向（适配悬浮小窗/全屏切换）
        globalLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            updateCardLayoutByAvailableHeight()
        }
        binding.featureGrid.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.post { updateCardLayoutByAvailableHeight() }
    }

    private fun setupAccountAnalytics() {
        // 刷新按钮
        binding.accountAnalyticsCard.onRefreshClick = {
            accountViewModel.defaultAccount.value?.let { account ->
                accountAnalyticsViewModel.refresh(account)
            }
        }

        // 时间范围切换
        binding.accountAnalyticsCard.onTimeRangeChanged = { timeRange ->
            accountViewModel.defaultAccount.value?.let { account ->
                accountAnalyticsViewModel.changeTimeRange(account, timeRange)
            }
        }

        // 开关监听：开启时自动加载数据
        binding.accountAnalyticsCard.onAnalyticsEnabledChanged = { isEnabled ->
            updateFeatureGridLayout(isEnabled)
            if (isEnabled) {
                accountViewModel.defaultAccount.value?.let { account ->
                    accountAnalyticsViewModel.refresh(account)
                }
            }
        }

        // 初始化时根据开关状态设置布局模式
        updateFeatureGridLayout(binding.accountAnalyticsCard.isAnalyticsEnabled())
    }

    /**
     * 根据分析开关状态切换功能网格布局模式：
     * - 分析开启：GridLayout wrap_content，卡片固定高度，内容可滚动
     * - 分析关闭：GridLayout 占满剩余空间，卡片均匀分布，不留白
     */
    private fun updateFeatureGridLayout(analyticsEnabled: Boolean) {
        val grid = binding.featureGrid
        val container = binding.homeContainer
        val childCount = grid.childCount

        if (analyticsEnabled) {
            // 分析开启：wrap_content 模式，内容可滚动
            val containerParams = container.layoutParams
            containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            container.layoutParams = containerParams

            val gridParams = grid.layoutParams as LinearLayout.LayoutParams
            gridParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            gridParams.weight = 0f
            grid.layoutParams = gridParams

            for (i in 0 until childCount) {
                val child = grid.getChildAt(i)
                val params = child.layoutParams as GridLayout.LayoutParams
                params.height = GridLayout.LayoutParams.WRAP_CONTENT
                params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                child.layoutParams = params
            }
            // 布局变化后重新计算卡片方向
            grid.post { updateCardLayoutByAvailableHeight() }
        } else {
            // 分析关闭：填满剩余空间，卡片平分高度，不留白
            val containerParams = container.layoutParams
            containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            container.layoutParams = containerParams

            val gridParams = grid.layoutParams as LinearLayout.LayoutParams
            gridParams.height = 0
            gridParams.weight = 1f
            grid.layoutParams = gridParams

            for (i in 0 until childCount) {
                val child = grid.getChildAt(i)
                val params = child.layoutParams as GridLayout.LayoutParams
                params.height = 0
                params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                child.layoutParams = params
            }
            // 布局变化后重新计算卡片方向
            grid.post { updateCardLayoutByAvailableHeight() }
        }
    }

    /**
     * 根据可用高度调整卡片布局：
     * - 高度充足（全屏等）：纵向布局，图标在上，文字在下
     * - 高度不足（悬浮小窗等）：横向布局，图标在左，文字在右
     */
    private fun updateCardLayoutByAvailableHeight() {
        // 安全检查：Fragment 已销毁时直接返回
        val binding = _binding ?: return
        val grid = binding.featureGrid
        val childCount = grid.childCount
        if (childCount == 0) return

        // 获取可用高度
        val availableHeight = grid.height
        if (availableHeight <= 0) {
            // 布局还未完成，延迟再试
            grid.post { updateCardLayoutByAvailableHeight() }
            return
        }

        // 计算行数（两列）
        val rowCount = (childCount + 1) / 2
        // 每行可用高度
        val rowHeight = availableHeight / rowCount

        // 阈值：当每行高度小于 80dp 时，认为高度不足，切换为横向布局
        val density = resources.displayMetrics.density
        val minHeightForVertical = (80 * density).toInt()
        val useHorizontal = rowHeight < minHeightForVertical

        // 方向未变化则跳过，避免重复修改布局导致循环触发 onGlobalLayout
        if (isCardHorizontal == useHorizontal) return
        isCardHorizontal = useHorizontal

        // 遍历所有卡片调整内部布局
        for (i in 0 until childCount) {
            val card = grid.getChildAt(i) as? com.google.android.material.card.MaterialCardView ?: continue
            val innerLayout = card.getChildAt(0) as? LinearLayout ?: continue
            val imageView = innerLayout.getChildAt(0) as? android.widget.ImageView ?: continue
            val textView = innerLayout.getChildAt(1) as? android.widget.TextView ?: continue

            if (useHorizontal) {
                // 横向布局：图标在左，文字在右
                innerLayout.orientation = LinearLayout.HORIZONTAL
                innerLayout.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START

                // 调整 padding
                val paddingH = (12 * density).toInt()
                val paddingV = (8 * density).toInt()
                innerLayout.setPadding(paddingH, paddingV, paddingH, paddingV)

                // 调整图标大小
                val iconSize = (28 * density).toInt()
                val iconParams = imageView.layoutParams as LinearLayout.LayoutParams
                iconParams.width = iconSize
                iconParams.height = iconSize
                iconParams.topMargin = 0
                iconParams.marginEnd = (10 * density).toInt()
                imageView.layoutParams = iconParams

                // 调整文字
                val textParams = textView.layoutParams as LinearLayout.LayoutParams
                textParams.width = 0
                textParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                textParams.weight = 1f
                textParams.topMargin = 0
                textView.layoutParams = textParams
                textView.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                textView.textSize = 13f
            } else {
                // 纵向布局：图标在上，文字在下（恢复默认）
                innerLayout.orientation = LinearLayout.VERTICAL
                innerLayout.gravity = android.view.Gravity.CENTER

                // 调整 padding
                val padding = (12 * density).toInt()
                innerLayout.setPadding(padding, padding, padding, padding)

                // 调整图标大小
                val iconSize = (36 * density).toInt()
                val iconParams = imageView.layoutParams as LinearLayout.LayoutParams
                iconParams.width = iconSize
                iconParams.height = iconSize
                iconParams.topMargin = 0
                iconParams.marginEnd = 0
                imageView.layoutParams = iconParams

                // 调整文字
                val textParams = textView.layoutParams as LinearLayout.LayoutParams
                textParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                textParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                textParams.weight = 0f
                textParams.topMargin = (8 * density).toInt()
                textView.layoutParams = textParams
                textView.gravity = android.view.Gravity.CENTER
                textView.textSize = 14f
            }
        }
    }

    private fun setupUI() {
                binding.aboutCard.setOnClickListener {
                    AnimationHelper.scaleDown(it)
                    it.postDelayed({
                        showAboutDialog()
                    }, 150)
                }
        
        binding.workerCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_worker)
            }, 150)
        }
        
        binding.dnsCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_domainList)
            }, 150)
        }
        
        binding.customDomainCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_route)
            }, 150)
        }
        
        binding.kvCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_kv)
            }, 150)
        }
        
        binding.pagesCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_pages)
            }, 150)
        }
        
        binding.r2Card.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_r2)
            }, 150)
        }
        
        binding.d1Card.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_d1)
            }, 150)
        }
        
        binding.backupCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_backup)
            }, 150)
        }
        
        binding.zeroTrustCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_zerotrust)
            }, 150)
        }

        binding.storeCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().safeNavigate(R.id.action_home_to_store)
            }, 150)
        }
        
        binding.aboutCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            showAboutDialog()
        }

        binding.settingsCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                showSettingsDialog()
            }, 150)
        }
    }

    private fun showSettingsDialog() {
        val dialogBinding = com.muort.upworker.databinding.DialogSettingsBinding.inflate(
            LayoutInflater.from(requireContext())
        )

        // 初始化主题模式
        val themeMode = ThemeHelper.getThemeMode(requireContext())
        val themeButtonId = when (themeMode) {
            ThemeHelper.THEME_LIGHT -> R.id.themeLightBtn
            ThemeHelper.THEME_DARK -> R.id.themeDarkBtn
            else -> R.id.themeFollowSystemBtn
        }
        dialogBinding.themeModeToggleGroup.check(themeButtonId)

        dialogBinding.themeModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.themeLightBtn -> ThemeHelper.THEME_LIGHT
                    R.id.themeDarkBtn -> ThemeHelper.THEME_DARK
                    else -> ThemeHelper.THEME_FOLLOW_SYSTEM
                }
                ThemeHelper.setThemeMode(requireContext(), mode)
                // 延迟重启，让用户看到按钮切换效果
                view?.postDelayed({
                    if (isAdded) activity?.recreate()
                }, 200)
            }
        }

        // 动态配色
        val dynamicAvailable = ThemeHelper.isDynamicColorAvailable()
        if (dynamicAvailable) {
            dialogBinding.dynamicColorSwitch.isChecked =
                ThemeHelper.isDynamicColorEnabled(requireContext())
            dialogBinding.dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
                ThemeHelper.setDynamicColorEnabled(requireContext(), isChecked)
                view?.postDelayed({
                    if (isAdded) activity?.recreate()
                }, 200)
            }
        } else {
            dialogBinding.dynamicColorLayout.visibility = View.GONE
        }

        // 语言设置
        val language = LocaleHelper.getLanguage(requireContext())
        val langButtonId = when (language) {
            LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE -> R.id.langChineseBtn
            LocaleHelper.LANGUAGE_ENGLISH -> R.id.langEnglishBtn
            else -> R.id.langFollowSystemBtn
        }
        dialogBinding.languageToggleGroup.check(langButtonId)

        dialogBinding.languageToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val lang = when (checkedId) {
                    R.id.langChineseBtn -> LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE
                    R.id.langEnglishBtn -> LocaleHelper.LANGUAGE_ENGLISH
                    else -> LocaleHelper.LANGUAGE_FOLLOW_SYSTEM
                }
                LocaleHelper.setLanguage(requireContext(), lang)
                // 延迟重启，让用户看到按钮切换效果
                view?.postDelayed({
                    if (isAdded) activity?.recreate()
                }, 200)
            }
        }

        // 显示大小：6 档分两行 ToggleGroup，代码层保持互斥（单选）；选中后立即保存并 recreate。
        val sizeOptions = DisplaySizeHelper.getOptions(requireContext())
        val selectedIdx = DisplaySizeHelper.getSelectedIndex(requireContext())
        // Map: Button id → options index (0..5)
        val idToIndex = mapOf(
            R.id.displaySizeExtraSmallBtn to 0,
            R.id.displaySizeSmallerBtn    to 1,
            R.id.displaySizeSmallBtn      to 2,
            R.id.displaySizeDefaultBtn    to 3,
            R.id.displaySizeLargeBtn      to 4,
            R.id.displaySizeExtraLargeBtn to 5,
        )
        val indexToId = idToIndex.entries.associate { (k, v) -> v to k }
        // 初始化：把当前档对应的按钮设为 checked
        indexToId[selectedIdx]?.let { id ->
            if (idToIndex[id]!! < 3) dialogBinding.displaySizeRow1.check(id)
            else dialogBinding.displaySizeRow2.check(id)
        }
        // 互斥 + 应用新值
        fun onSizeChecked(checkedId: Int, whichRow: Int) {
            if (checkedId == View.NO_ID) return
            val idx = idToIndex[checkedId] ?: return
            // 清掉另一个 ToggleGroup 的选中，避免双选中态视觉
            if (whichRow == 1) dialogBinding.displaySizeRow2.clearChecked()
            else dialogBinding.displaySizeRow1.clearChecked()
            val scale = sizeOptions[idx].second
            if (scale != DisplaySizeHelper.getFontScale(requireContext())) {
                DisplaySizeHelper.setFontScale(requireContext(), scale)
                // 延迟重启，让用户看到按钮切换效果
                view?.postDelayed({
                    if (isAdded) requireActivity().recreate()
                }, 250)
            }
        }
        dialogBinding.displaySizeRow1.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onSizeChecked(checkedId, 1)
        }
        dialogBinding.displaySizeRow2.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onSizeChecked(checkedId, 2)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_title)
            .setView(dialogBinding.root)
            .show()

        // 自定义完成按钮
        dialogBinding.settingsDoneButton.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showAboutDialog() {
        val dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(requireContext()))
        
        // 自动读取版本号
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            dialogBinding.tvVersion.text = getString(R.string.about_version, versionName)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version name")
        }
        
        // 动态设置版权年份
        dialogBinding.tvCopyright.text = getString(R.string.about_copyright, Year.now().value)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        
        // App 图标点击打开 HTTP 日志
        dialogBinding.appIcon.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.muort.upworker.feature.log.LogActivity::class.java))
        }
        
        // Telegram 链接点击
        dialogBinding.layoutTelegram.setOnClickListener {
            openUrl("https://telegram.me/CFmuort")
        }
        
        // GitHub 链接点击
        dialogBinding.layoutGithub.setOnClickListener {
            openUrl("https://github.com/a422015028/CloudFlareAssistant")
        }

        // Cloudflare API 文档链接点击
        dialogBinding.layoutCloudflareApi.setOnClickListener {
            openUrl("https://developers.cloudflare.com/api/")
        }

        // Cloudflare 官网链接点击
        dialogBinding.layoutCloudflareWebsite.setOnClickListener {
            openUrl("https://www.cloudflare.com")
        }

        // 本应用官网链接点击
        dialogBinding.layoutAppWebsite.setOnClickListener {
            openUrl("https://cf.muort.com")
        }

        // API 令牌获取说明展开/收起
        dialogBinding.tvApiTokenGuideTitle.setOnClickListener {
            val content = dialogBinding.tvApiTokenGuideContent
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                dialogBinding.tvApiTokenGuideTitle.setText(R.string.about_api_token_guide_title_collapse)
            } else {
                content.visibility = View.GONE
                dialogBinding.tvApiTokenGuideTitle.setText(R.string.about_api_token_guide_title_expand)
            }
        }
        
        // 检查更新按钮点击
        dialogBinding.btnCheckUpdate.setOnClickListener {
            checkForUpdates(dialogBinding.pbLoading, dialogBinding.tvCheckUpdate)
        }
        
        dialog.show()
    }
    
    private fun checkForUpdates(progressBar: android.widget.ProgressBar, textView: android.widget.TextView) {
        val (currentVersionName, currentVersionCode) = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            packageInfo.versionName.orEmpty() to code
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version info")
            return
        }
        
        // 显示加载动画
        progressBar.visibility = android.view.View.VISIBLE
        textView.setText(R.string.checking)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updateInfo = fetchVersionInfo()
                
                // 恢复按钮状态
                progressBar.visibility = android.view.View.GONE
                textView.setText(R.string.check_update)
                
                if (updateInfo != null) {
                    val latestVersionCode = updateInfo.versionCode
                    if (latestVersionCode > currentVersionCode || isNewerVersion(updateInfo.versionName, currentVersionName)) {
                        showUpdateDialog(updateInfo.versionName, latestVersionCode, updateInfo.updateContent, updateInfo.apkUrl)
                    } else {
                        requireContext().showToast(getString(R.string.already_latest_version))
                    }
                } else {
                    requireContext().showToast(getString(R.string.check_update_failed))
                }
            } catch (e: Exception) {
                Timber.e(e, "Check update error")
                requireContext().showToast(getString(R.string.check_update_failed))
                
                // 恢复按钮状态
                progressBar.visibility = android.view.View.GONE
                textView.setText(R.string.check_update)
            }
        }
    }
    
    private suspend fun fetchVersionInfo(): VersionInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url("https://cf.muort.com/version")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val json = JSONObject(response.body!!.string())
                    val versionName = json.optString("versionName", "")
                    val versionCode = json.optLong("versionCode", 0)
                    val updateContent = json.optString("updateContent", "")
                    val apkUrl = json.optString("apk", "")
                    if (versionCode > 0) {
                        return@withContext VersionInfo(versionName, versionCode, updateContent, apkUrl)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Fetch version info error")
            }
            return@withContext null
        }
    }
    
    private fun showUpdateDialog(versionName: String, versionCode: Long, updateContent: String, apkUrl: String) {
        val message = if (updateContent.isNotBlank()) {
            getString(R.string.version_with_build_format, versionName, versionCode) + "\n\n$updateContent"
        } else {
            getString(R.string.new_version_available, getString(R.string.version_format, versionName))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_version_found)
            .setMessage(message)
            .setPositiveButton(R.string.update) { _, _ ->
                if (apkUrl.isNotBlank()) {
                    openUrl(apkUrl)
                } else {
                    requireContext().showToast(getString(R.string.invalid_download_url))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private data class VersionInfo(val versionName: String, val versionCode: Long, val updateContent: String, val apkUrl: String)
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            requireContext().showToast(getString(R.string.cannot_open_url))
            Timber.e(e, "Failed to open URL: $url")
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        // 仅在账户分析开关开启时加载数据
                        if (account != null && binding.accountAnalyticsCard.isAnalyticsEnabled()) {
                            accountAnalyticsViewModel.load(account)
                        }
                    }
                }

                launch {
                    accountAnalyticsViewModel.state.collect { state ->
                        when (state) {
                            is AccountAnalyticsState.Idle -> {
                                // 初始状态，不显示任何内容
                            }
                            is AccountAnalyticsState.Loading -> {
                                binding.accountAnalyticsCard.showLoading()
                            }
                            is AccountAnalyticsState.Success -> {
                                binding.accountAnalyticsCard.showData(state.overview)
                            }
                            is AccountAnalyticsState.Error -> {
                                binding.accountAnalyticsCard.showError(state.message.asString(requireContext()))
                                Timber.e("Account analytics error: ${state.message}")
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        // 移除布局监听，避免 Fragment 销毁后回调导致 NPE
        globalLayoutListener?.let {
            view?.findViewById<android.view.View>(R.id.featureGrid)?.viewTreeObserver?.removeOnGlobalLayoutListener(it)
        }
        globalLayoutListener = null
        isCardHorizontal = null
        super.onDestroyView()
        _binding = null
    }
}
