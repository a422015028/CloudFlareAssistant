package com.muort.upworker.feature.zerotrust.posture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.muort.upworker.R
import com.muort.upworker.databinding.FragmentDevicePostureBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Device posture management - posture checks and third-party EDR/UEM integrations.
 */
@AndroidEntryPoint
class DevicePostureFragment : Fragment() {

    private var _binding: FragmentDevicePostureBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicePostureViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicePostureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.postureViewPager.adapter = DevicePosturePagerAdapter(this)
        TabLayoutMediator(binding.postureTabLayout, binding.postureViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.zt_posture_tab_rules)
                1 -> getString(R.string.zt_posture_tab_integrations)
                else -> ""
            }
        }.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loadingState.collect { loading ->
                        binding.postureProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.message.collect {
                        Toast.makeText(requireContext(), it.asString(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.error.collect {
                        Toast.makeText(requireContext(), it.asString(requireContext()), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Toast.makeText(requireContext(), R.string.app_no_account_selected, Toast.LENGTH_SHORT).show()
        } else {
            viewModel.loadRules(account)
            viewModel.loadIntegrations(account)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
