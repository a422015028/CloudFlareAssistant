package com.muort.upworker.feature.zerotrust.sshca

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.databinding.FragmentSshCaBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for Access for Infrastructure SSH Certificate Authority (Gateway CA).
 * Shows the account-level SSH CA public key, or offers to generate one if not yet created.
 */
@AndroidEntryPoint
class SshCaFragment : Fragment() {

    private var _binding: FragmentSshCaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SshCaViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSshCaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
        loadCa()
    }

    private fun setupClickListeners() {
        binding.sshCaGenerateBtn.setOnClickListener {
            showGenerateConfirmDialog()
        }

        binding.sshCaCopyBtn.setOnClickListener {
            val publicKey = viewModel.ca.value?.publicKey
            if (!publicKey.isNullOrBlank()) {
                copyToClipboard(publicKey)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loadingState.collect { loading ->
                        binding.sshCaProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.ca.collect { ca ->
                        updateUiState(ca)
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

    private fun updateUiState(ca: com.muort.upworker.core.model.GatewayCa?) {
        if (ca != null && !ca.publicKey.isNullOrBlank()) {
            // CA exists
            binding.sshCaEmptyState.visibility = View.GONE
            binding.sshCaContentState.visibility = View.VISIBLE
            binding.sshCaPublicKeyText.text = ca.publicKey
        } else {
            // No CA yet
            binding.sshCaEmptyState.visibility = View.VISIBLE
            binding.sshCaContentState.visibility = View.GONE
        }
    }

    private fun showGenerateConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_ssh_ca_generate)
            .setMessage(R.string.zt_ssh_ca_generate_confirm)
            .setPositiveButton(R.string.zt_ssh_ca_generate) { _, _ ->
                generateCa()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateCa() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Toast.makeText(requireContext(), R.string.app_no_account_selected, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.generateCa(account)
    }

    private fun loadCa() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Toast.makeText(requireContext(), R.string.app_no_account_selected, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.loadCa(account)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SSH CA Public Key", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.msg_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CLIP_LABEL_PUBLIC_KEY = "SSH CA Public Key"
    }
}
