package com.muort.upworker.feature.home

import com.muort.upworker.core.log.LogRepository
import kotlinx.coroutines.flow.collectLatest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogAboutBinding
import com.muort.upworker.databinding.FragmentHomeBinding
import com.muort.upworker.feature.account.AccountViewModel
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
    private val dashboardViewModel: DashboardViewModel by viewModels()
    
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
        setupDashboard()

        // 日志卡片点击跳转新页面
        binding.logCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.muort.upworker.feature.log.LogActivity::class.java))
        }
        // 日志开关已移至日志页面
    }

    private fun setupDashboard() {
        // 设置刷新按钮点击监听
        binding.dashboardCard.onRefreshClick = {
            accountViewModel.defaultAccount.value?.let { account ->
                dashboardViewModel.refresh(account)
            }
        }
        
        // 设置时间范围切换监听
        binding.dashboardCard.onTimeRangeChanged = { timeRange ->
            accountViewModel.defaultAccount.value?.let { account ->
                dashboardViewModel.changeTimeRange(account, timeRange)
            }
        }
        
        // 设置仪表盘开关监听
        binding.dashboardCard.onDashboardEnabledChanged = { isEnabled ->
            if (isEnabled) {
                // 开启时自动刷新数据
                accountViewModel.defaultAccount.value?.let { account ->
                    dashboardViewModel.refresh(account)
                }
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
                findNavController().navigate(R.id.action_home_to_worker)
            }, 150)
        }
        
        binding.dnsCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_dns)
            }, 150)
        }
        
        binding.routeCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_route)
            }, 150)
        }
        
        binding.kvCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_kv)
            }, 150)
        }
        
        binding.pagesCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_pages)
            }, 150)
        }
        
        binding.r2Card.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_r2)
            }, 150)
        }
        
        binding.d1Card.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_d1)
            }, 150)
        }
        
        binding.backupCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_backup)
            }, 150)
        }
        
        binding.zeroTrustCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_zerotrust)
            }, 150)
        }
        
        binding.aboutCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            showAboutDialog()
        }
    }
    
    private fun showAboutDialog() {
        val dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(requireContext()))
        
        // 自动读取版本号
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            dialogBinding.tvVersion.text = "版本 $versionName"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version name")
        }
        
        // 动态设置版权年份
        dialogBinding.tvCopyright.text = "© ${Year.now().value} CloudFlare Assistant\nMIT License"
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        
        // Telegram 链接点击
        dialogBinding.layoutTelegram.setOnClickListener {
            openUrl("https://t.me/CFmuort")
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
            openUrl("https://www.cloudflare.com/")
        }

        // 本应用官网链接点击
        dialogBinding.layoutAppWebsite.setOnClickListener {
            openUrl("https://cf.390202.xyz/")
        }

        // API 令牌获取说明展开/收起
        dialogBinding.tvApiTokenGuideTitle.setOnClickListener {
            val content = dialogBinding.tvApiTokenGuideContent
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                dialogBinding.tvApiTokenGuideTitle.text = "🔑 如何获取 Cloudflare API 令牌（点击收起）"
            } else {
                content.visibility = View.GONE
                dialogBinding.tvApiTokenGuideTitle.text = "🔑 如何获取 Cloudflare API 令牌（点击展开）"
            }
        }
        
        // 检查更新按钮点击
        dialogBinding.btnCheckUpdate.setOnClickListener {
            checkForUpdates(dialogBinding.pbLoading, dialogBinding.tvCheckUpdate)
        }
        
        dialog.show()
    }
    
    private fun checkForUpdates(progressBar: android.widget.ProgressBar, textView: android.widget.TextView) {
        val currentVersionCode = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).longVersionCode
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version code")
            return
        }
        
        // 显示加载动画
        progressBar.visibility = android.view.View.VISIBLE
        textView.text = "检查中..."
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updateInfo = fetchVersionInfo()
                
                // 恢复按钮状态
                progressBar.visibility = android.view.View.GONE
                textView.text = "检查更新"
                
                if (updateInfo != null) {
                    val latestVersionCode = updateInfo.versionCode
                    if (latestVersionCode > currentVersionCode) {
                        showUpdateDialog(updateInfo.versionName)
                    } else {
                        requireContext().showToast("当前已是最新版本")
                    }
                } else {
                    requireContext().showToast("检查更新失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "Check update error")
                requireContext().showToast("检查更新失败")
                
                // 恢复按钮状态
                progressBar.visibility = android.view.View.GONE
                textView.text = "检查更新"
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
                    .url("https://cfd.390202.xyz/version")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val json = JSONObject(response.body!!.string())
                    val versionName = json.optString("versionName", "")
                    val versionCode = json.optLong("versionCode", 0)
                    if (versionCode > 0) {
                        return@withContext VersionInfo(versionName, versionCode)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Fetch version info error")
            }
            return@withContext null
        }
    }
    
    private fun showUpdateDialog(versionName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("发现新版本")
            .setMessage("版本 $versionName 可用，是否立即更新？")
            .setPositiveButton("更新") { _, _ ->
                openUrl("https://cfd.390202.xyz/CloudFlareAssistant.apk")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private data class VersionInfo(val versionName: String, val versionCode: Long)
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            requireContext().showToast("无法打开链接")
            Timber.e(e, "Failed to open URL: $url")
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        // 仅在仪表盘开关开启时加载数据
                        if (account != null && binding.dashboardCard.isDashboardEnabled()) {
                            dashboardViewModel.loadDashboard(account)
                        }
                    }
                }
                
                launch {
                    dashboardViewModel.dashboardState.collect { state ->
                        when (state) {
                            is DashboardState.Idle -> {
                                // 初始状态，不显示任何内容
                            }
                            is DashboardState.Loading -> {
                                binding.dashboardCard.showLoading()
                            }
                            is DashboardState.Success -> {
                                binding.dashboardCard.showData(state.metrics)
                            }
                            is DashboardState.Error -> {
                                binding.dashboardCard.showError(state.message)
                                Timber.e("Dashboard error: ${state.message}")
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
