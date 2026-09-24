package com.muort.upworker.feature.zerotrust.mtls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.tabs.TabLayoutMediator
import com.muort.upworker.R
import com.muort.upworker.databinding.FragmentMtlsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.widget.Toast

/**
 * Mutual TLS management - root certificates and per-hostname edge settings.
 */
@AndroidEntryPoint
class MtlsFragment : Fragment() {

    private var _binding: FragmentMtlsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MtlsViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMtlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mtlsViewPager.adapter = MtlsPagerAdapter(this)
        TabLayoutMediator(binding.mtlsTabLayout, binding.mtlsViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.zt_mtls_tab_certificates)
                1 -> getString(R.string.zt_mtls_tab_settings)
                else -> ""
            }
        }.attach()

        observeViewModel()
        loadData()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loadingState.collect { loading ->
                        binding.mtlsProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
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
    }

    private fun loadData() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Toast.makeText(requireContext(), R.string.app_no_account_selected, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.loadCertificates(account)
        viewModel.loadSettings(account)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
