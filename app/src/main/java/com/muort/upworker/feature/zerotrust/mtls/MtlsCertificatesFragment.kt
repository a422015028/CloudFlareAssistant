package com.muort.upworker.feature.zerotrust.mtls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.MtlsCertificate
import com.muort.upworker.core.model.MtlsCertificateCreateRequest
import com.muort.upworker.core.model.MtlsCertificateUpdateRequest
import com.muort.upworker.databinding.FragmentMtlsCertificatesBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MtlsCertificatesFragment : Fragment() {

    private var _binding: FragmentMtlsCertificatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MtlsViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var certificateAdapter: MtlsCertificateAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMtlsCertificatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        binding.createCertificateButton.setOnClickListener { showCreateDialog() }
        observeViewModel()
    }

    private fun setupRecyclerView() {
        certificateAdapter = MtlsCertificateAdapter(
            onCardClick = { cert -> showDetailDialog(cert) },
            onEditClick = { cert -> showEditDialog(cert) },
            onDeleteClick = { cert -> confirmDelete(cert) }
        )
        binding.certificatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = certificateAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.certificates.collect { certs ->
                    certificateAdapter.submitList(certs)
                    binding.certificatesEmptyView.visibility =
                        if (certs.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ==================== Create ====================

    private fun showCreateDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_mtls_certificate_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.certNameInput)
        val pemInput = dialogView.findViewById<TextInputEditText>(R.id.certPemInput)
        val hostnamesInput = dialogView.findViewById<TextInputEditText>(R.id.certHostnamesInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_cert_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val pem = normalizePem(pemInput.text?.toString().orEmpty())
                if (name.isEmpty()) {
                    toast(R.string.zt_mtls_cert_name_empty)
                    return@setPositiveButton
                }
                if (!isPemCertificate(pem)) {
                    toast(R.string.zt_mtls_cert_pem_invalid)
                    return@setPositiveButton
                }

                viewModel.createCertificate(
                    account,
                    MtlsCertificateCreateRequest(
                        name = name,
                        certificate = pem,
                        associatedHostnames = parseHostnames(hostnamesInput.text?.toString())
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Edit ====================

    private fun showEditDialog(cert: MtlsCertificate) {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_mtls_certificate_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.certNameInput)
        val pemInput = dialogView.findViewById<TextInputEditText>(R.id.certPemInput)
        val pemLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.certPemLayout)
        val hostnamesInput = dialogView.findViewById<TextInputEditText>(R.id.certHostnamesInput)

        nameInput.setText(cert.name.orEmpty())
        pemLayout.visibility = View.GONE // certificate content cannot be changed after creation
        hostnamesInput.setText(cert.associatedHostnames.orEmpty().joinToString("\n"))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_cert_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(R.string.zt_mtls_cert_name_empty)
                    return@setPositiveButton
                }

                viewModel.updateCertificate(
                    account,
                    cert.id,
                    MtlsCertificateUpdateRequest(
                        name = name,
                        associatedHostnames = parseHostnames(hostnamesInput.text?.toString()) ?: emptyList()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Detail ====================

    private fun showDetailDialog(cert: MtlsCertificate) {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_mtls_certificate_detail, null)

        val hosts = cert.associatedHostnames.orEmpty()
        dialogView.findViewById<TextView>(R.id.detailCertHostnamesText).text =
            hosts.joinToString("\n").ifEmpty { ctx.getString(R.string.zt_mtls_cert_no_hostnames) }
        dialogView.findViewById<TextView>(R.id.detailCertFingerprintText).text =
            cert.fingerprint ?: ctx.getString(R.string.status_unknown)
        dialogView.findViewById<TextView>(R.id.detailCertExpiresText).text =
            ctx.getString(R.string.zt_service_token_expires, formatDateTime(cert.expiresOn) ?: ctx.getString(R.string.status_unknown))
        dialogView.findViewById<TextView>(R.id.detailCertCreatedText).text =
            ctx.getString(R.string.token_detail_created_time, formatDateTime(cert.createdAt) ?: ctx.getString(R.string.status_unknown))
        dialogView.findViewById<TextView>(R.id.detailCertUpdatedText).text =
            ctx.getString(R.string.zt_device_updated_label, formatDateTime(cert.updatedAt) ?: ctx.getString(R.string.status_unknown))
        dialogView.findViewById<TextView>(R.id.detailCertIdText).text =
            ctx.getString(R.string.d1_db_id_label, cert.id)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(cert.name ?: ctx.getString(R.string.status_unknown))
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .create()

        dialogView.findViewById<TextView>(R.id.detailCertEditButton).setOnClickListener {
            dialog.dismiss()
            showEditDialog(cert)
        }
        dialogView.findViewById<TextView>(R.id.detailCertDeleteButton).setOnClickListener {
            dialog.dismiss()
            confirmDelete(cert)
        }

        dialog.show()
    }

    // ==================== Delete ====================

    private fun confirmDelete(cert: MtlsCertificate) {
        val account = accountViewModel.defaultAccount.value ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_cert_delete_title)
            .setMessage(getString(R.string.zt_mtls_cert_delete_confirm, cert.name ?: cert.id))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteCertificate(account, cert.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Helpers ====================

    private fun parseHostnames(raw: String?): List<String>? {
        val list = raw.orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return list.ifEmpty { null }
    }

    private fun normalizePem(raw: String): String =
        raw.trim().replace("\r\n", "\n").replace("\r", "\n")

    private fun isPemCertificate(pem: String): Boolean =
        pem.contains("-----BEGIN CERTIFICATE-----") && pem.contains("-----END CERTIFICATE-----")

    private fun formatDateTime(raw: String?): String? =
        raw?.let {
            runCatching {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .format(Instant.parse(it).atZone(ZoneId.systemDefault()))
            }.getOrNull()
        }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
