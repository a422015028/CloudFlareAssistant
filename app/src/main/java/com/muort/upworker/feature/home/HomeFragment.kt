package com.muort.upworker.feature.home

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
import com.muort.upworker.R
import com.muort.upworker.core.util.AnimationHelper
import com.muort.upworker.databinding.FragmentHomeBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    
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
    }
    
    private fun setupUI() {
        animateFeatureCards()
        
        binding.manageAccountsBtn.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_accounts)
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
        
        binding.backupCard.setOnClickListener {
            AnimationHelper.scaleDown(it)
            it.postDelayed({
                findNavController().navigate(R.id.action_home_to_backup)
            }, 150)
        }
    }
    
    private fun animateFeatureCards() {
        val cards = listOf(
            binding.workerCard,
            binding.dnsCard,
            binding.routeCard,
            binding.kvCard,
            binding.pagesCard,
            binding.r2Card,
            binding.backupCard
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
