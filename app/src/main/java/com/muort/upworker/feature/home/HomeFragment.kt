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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class HomeFragment : Fragment() {
        override fun onAttach(context: android.content.Context) {
            super.onAttach(context)
            LogRepository.init(context.applicationContext)
        }
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    
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

        // 日志卡片点击跳转新页面
        binding.logCard.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.muort.upworker.feature.log.LogActivity::class.java))
        }
        // 日志开关已移至日志页面
    }

    private fun setupUI() {
                binding.aboutBtn.setOnClickListener {
                    showAboutDialog()
                }
        animateFeatureCards()
        
        binding.manageAccountsBtn.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_accounts)
        }

                // 点击整个账号卡片也可进入账号管理/编辑
                binding.accountCard.setOnClickListener {
                    AnimationHelper.scaleDown(it)
                    it.postDelayed({
                        val accountId = accountViewModel.defaultAccount.value?.id ?: -1L
                        if (accountId != -1L) {
                            // 跳转到 AccountEditFragment 并传递 accountId
                            findNavController().navigate(
                                R.id.accountEditFragment,
                                android.os.Bundle().apply { putLong("accountId", accountId) }
                            )
                        } else {
                            // 没有账号时跳转到账号管理
                            findNavController().navigate(R.id.action_home_to_accounts)
                        }
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
        
        binding.aboutCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            showAboutDialog()
        }
    }
    
    private fun animateFeatureCards() {
        val cards = listOf(
            binding.workerCard,
            binding.pagesCard,
            binding.dnsCard,
            binding.routeCard,
            binding.kvCard,
            binding.r2Card,
            binding.d1Card,
            binding.backupCard,
            binding.logCard
        )
        
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.postDelayed({
                card.animate()
                    .alpha(1f)
                    .translationYBy(-30f)
                    .setDuration(300)
                    .start()
            }, (index * 50).toLong())
        }
    }
    
    private fun showAboutDialog() {
        val dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(requireContext()))
        
        // 自动读取版本号
        try {
            val versionName = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            dialogBinding.tvVersion.text = "版本 $versionName"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version name")
        }
        
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
        
        dialog.show()
    }
    
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
                accountViewModel.defaultAccount.collect { account ->
                    binding.accountNameText.text = account?.name ?: "未选择账号"
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
