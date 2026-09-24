package com.muort.upworker.feature.zerotrust.servicetoken

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.ServiceToken
import com.muort.upworker.core.model.ServiceTokenRequest
import com.muort.upworker.databinding.FragmentServiceTokensBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Fragment for managing Access Service Tokens.
 * Covers the full API surface: list, get(detail), create, update(rename/duration/enable),
 * delete, refresh(expiration), rotate(secret with optional grace period).
 */
@AndroidEntryPoint
class ServiceTokensFragment : Fragment() {

    private var _binding: FragmentServiceTokensBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServiceTokensViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var tokenAdapter: ServiceTokenAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServiceTokensBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadTokens()
    }

    private fun setupRecyclerView() {
        tokenAdapter = ServiceTokenAdapter(
            onCardClick = { token -> openDetail(token) },
            onCopyIdClick = { token -> copyText(token.clientId.orEmpty(), CLIP_LABEL_ID) },
            onMoreClick = { anchor, token -> showMoreMenu(anchor, token) }
        )

        binding.tokensRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tokenAdapter
        }
    }

    private fun setupClickListeners() {
        binding.createTokenButton.setOnClickListener {
            showCreateTokenDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tokens.collect { tokens ->
                        tokenAdapter.submitList(tokens)
                        binding.emptyView.visibility = if (tokens.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.loadingState.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        Toast.makeText(requireContext(), message.asString(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        Toast.makeText(requireContext(), error.asString(requireContext()), Toast.LENGTH_LONG).show()
                    }
                }

                launch {
                    viewModel.createdToken.collect { token ->
                        showSecretDialog(
                            token = token,
                            titleRes = R.string.zt_service_token_created_dialog_title,
                            warningRes = R.string.zt_service_token_secret_warning
                        )
                    }
                }

                launch {
                    viewModel.rotatedSecret.collect { rotated ->
                        val warningRes = if (rotated.previousRevokedImmediately) {
                            R.string.zt_service_token_rotate_warning_immediate
                        } else {
                            R.string.zt_service_token_rotate_warning_grace
                        }
                        showSecretDialog(
                            token = rotated.token,
                            titleRes = R.string.zt_service_token_rotated_dialog_title,
                            warningRes = warningRes
                        )
                    }
                }

                launch {
                    viewModel.detailToken.collect { token ->
                        showDetailDialog(token)
                    }
                }
            }
        }
    }

    private fun loadTokens() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Toast.makeText(requireContext(), R.string.app_no_account_selected, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.loadTokens(account)
    }

    // ==================== More menu ====================

    private fun showMoreMenu(anchor: View, token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return
        val popup = PopupMenu(requireContext(), anchor)
        val menu = popup.menu

        menu.add(0, MENU_DETAIL, 0, R.string.zt_service_token_menu_detail)
        menu.add(0, MENU_REFRESH, 1, R.string.zt_tunnel_refresh_token)
            .isVisible = token.duration != ServiceTokenAdapter.DURATION_FOREVER
        menu.add(0, MENU_ROTATE, 2, R.string.zt_service_token_action_rotate)
        menu.add(0, MENU_EDIT, 3, R.string.edit)
        menu.add(
            0,
            MENU_TOGGLE_ENABLED,
            4,
            if (token.enabled == false) R.string.zt_service_token_action_enable
            else R.string.zt_service_token_action_disable
        )
        menu.add(0, MENU_DELETE, 5, R.string.delete)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DETAIL -> openDetail(token)
                MENU_REFRESH -> confirmRefresh(token)
                MENU_ROTATE -> showRotateDialog(token)
                MENU_EDIT -> showEditTokenDialog(token)
                MENU_TOGGLE_ENABLED -> toggleEnabled(token)
                MENU_DELETE -> confirmDeleteToken(token)
            }
            true
        }
        popup.show()
    }

    private fun openDetail(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return
        // Fetch the freshest state (GET) before showing the detail dialog
        viewModel.loadTokenDetail(account, token.id)
    }

    // ==================== Detail ====================

    private fun showDetailDialog(token: ServiceToken) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_service_token_detail, null)
        val ctx = requireContext()

        dialogView.findViewById<TextView>(R.id.detailClientIdText).text =
            token.clientId ?: ctx.getString(R.string.status_unknown)

        dialogView.findViewById<TextView>(R.id.detailDurationText).text =
            ctx.getString(R.string.zt_service_token_detail_duration, humanDuration(token.duration))

        val expires = formatServiceTokenDateTime(token.expiresAt)
        dialogView.findViewById<TextView>(R.id.detailExpiresText).text = when {
            token.duration == ServiceTokenAdapter.DURATION_FOREVER ->
                ctx.getString(R.string.status_never_expires)
            expires != null -> ctx.getString(R.string.zt_service_token_expires, expires)
            else -> ctx.getString(R.string.zt_service_token_expires_unknown)
        }

        dialogView.findViewById<TextView>(R.id.detailLastSeenText).text =
            ctx.getString(
                R.string.token_detail_last_used,
                formatServiceTokenDateTime(token.lastSeenAt)
                    ?: ctx.getString(R.string.zt_service_token_never_used)
            )
        dialogView.findViewById<TextView>(R.id.detailCreatedText).text =
            ctx.getString(
                R.string.token_detail_created_time,
                formatServiceTokenDateTime(token.createdAt)
                    ?: ctx.getString(R.string.status_unknown)
            )
        dialogView.findViewById<TextView>(R.id.detailUpdatedText).text =
            ctx.getString(
                R.string.zt_device_updated_label,
                formatServiceTokenDateTime(token.updatedAt)
                    ?: ctx.getString(R.string.status_unknown)
            )
        dialogView.findViewById<TextView>(R.id.detailTokenIdText).text =
            ctx.getString(R.string.d1_db_id_label, token.id)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(token.name)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .create()

        dialogView.findViewById<TextView>(R.id.detailCopyClientIdButton).setOnClickListener {
            copyText(token.clientId.orEmpty(), CLIP_LABEL_ID)
        }

        val refreshButton = dialogView.findViewById<TextView>(R.id.detailRefreshButton)
        if (token.duration == ServiceTokenAdapter.DURATION_FOREVER) {
            refreshButton.visibility = View.GONE
        } else {
            refreshButton.setOnClickListener {
                dialog.dismiss()
                confirmRefresh(token)
            }
        }

        dialogView.findViewById<TextView>(R.id.detailRotateButton).setOnClickListener {
            dialog.dismiss()
            showRotateDialog(token)
        }
        dialogView.findViewById<TextView>(R.id.detailEditButton).setOnClickListener {
            dialog.dismiss()
            showEditTokenDialog(token)
        }

        val toggleButton = dialogView.findViewById<TextView>(R.id.detailToggleEnabledButton)
        toggleButton.setText(
            if (token.enabled == false) R.string.zt_service_token_action_enable
            else R.string.zt_service_token_action_disable
        )
        toggleButton.setOnClickListener {
            dialog.dismiss()
            toggleEnabled(token)
        }

        dialogView.findViewById<TextView>(R.id.detailDeleteButton).setOnClickListener {
            dialog.dismiss()
            confirmDeleteToken(token)
        }

        dialog.show()
    }

    // ==================== Create / Edit ====================

    private fun showCreateTokenDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_service_token_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.tokenNameInput)
        val durationDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.tokenDurationDropdown)
        val enabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.tokenEnabledSwitch)
        enabledSwitch.visibility = View.GONE // tokens are created enabled

        val durationSelection = setupDurationDropdown(durationDropdown, currentDuration = null)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.zt_service_token_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val duration = durationOptions[durationSelection.index].second

                viewModel.createToken(account, ServiceTokenRequest(name = name, duration = duration))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditTokenDialog(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_service_token_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.tokenNameInput)
        val durationDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.tokenDurationDropdown)
        val enabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.tokenEnabledSwitch)

        nameInput.setText(token.name)
        enabledSwitch.isChecked = token.enabled != false
        val durationSelection = setupDurationDropdown(durationDropdown, currentDuration = token.duration)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.zt_service_token_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val duration = durationOptions[durationSelection.index].second

                viewModel.updateToken(
                    account,
                    token.id,
                    ServiceTokenRequest(
                        name = name,
                        duration = duration,
                        enabled = enabledSwitch.isChecked
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupDurationDropdown(
        dropdown: android.widget.AutoCompleteTextView,
        currentDuration: String?
    ): DurationSelection {
        // Surface an unknown/custom current duration (e.g. "60m") as its own selectable entry
        val options = durationOptions.toMutableList()
        var selectedIndex = DURATION_DEFAULT_INDEX
        if (currentDuration != null) {
            val existing = options.indexOfFirst { it.second == currentDuration }
            if (existing >= 0) {
                selectedIndex = existing
            } else {
                options.add(0, R.string.zt_service_token_duration_custom to currentDuration)
                selectedIndex = 0
            }
        }

        val selection = DurationSelection(selectedIndex)
        val labels = options.map { (labelRes, value) ->
            if (labelRes == R.string.zt_service_token_duration_custom) {
                getString(R.string.zt_service_token_duration_custom, value)
            } else {
                getString(labelRes)
            }
        }
        dropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        dropdown.setText(labels[selectedIndex], false)
        dropdown.setOnItemClickListener { _, _, position, _ ->
            selection.index = position
        }
        return selection
    }

    // ==================== Enable / Disable ====================

    private fun toggleEnabled(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return
        val enabling = token.enabled == false

        if (enabling) {
            viewModel.updateToken(
                account,
                token.id,
                ServiceTokenRequest(name = token.name, duration = token.duration, enabled = true)
            )
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_disable_title)
            .setMessage(getString(R.string.zt_service_token_disable_confirm, token.name))
            .setPositiveButton(R.string.zt_service_token_action_disable) { _, _ ->
                viewModel.updateToken(
                    account,
                    token.id,
                    ServiceTokenRequest(name = token.name, duration = token.duration, enabled = false)
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Refresh ====================

    private fun confirmRefresh(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_refresh_title)
            .setMessage(
                getString(
                    R.string.zt_service_token_refresh_confirm,
                    token.name,
                    humanDuration(token.duration)
                )
            )
            .setPositiveButton(R.string.zt_tunnel_refresh_token) { _, _ ->
                viewModel.refreshToken(account, token.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Rotate ====================

    private fun showRotateDialog(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return

        var checkedItem = 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_rotate_title)
            .setSingleChoiceItems(R.array.zt_service_token_rotate_grace_options, checkedItem) { _, which ->
                checkedItem = which
            }
            .setPositiveButton(R.string.zt_service_token_action_rotate) { _, _ ->
                val previousExpiresAt = when (checkedItem) {
                    GRACE_24H -> Instant.now().plus(24, ChronoUnit.HOURS).toString()
                    GRACE_7D -> Instant.now().plus(7, ChronoUnit.DAYS).toString()
                    else -> null // immediate revocation
                }
                viewModel.rotateToken(account, token.id, previousExpiresAt)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Delete ====================

    private fun confirmDeleteToken(token: ServiceToken) {
        val account = accountViewModel.defaultAccount.value ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_service_token_delete_title)
            .setMessage(getString(R.string.zt_service_token_delete_confirm, token.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteToken(account, token.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Created/rotated secret (one-time) ====================

    private fun showSecretDialog(token: ServiceToken, titleRes: Int, warningRes: Int) {
        val clientId = token.clientId.orEmpty()
        val clientSecret = token.clientSecret.orEmpty()

        val dialogView = layoutInflater.inflate(R.layout.dialog_service_token_secret, null)
        dialogView.findViewById<TextView>(R.id.clientIdText).text = clientId
        dialogView.findViewById<TextView>(R.id.clientSecretText).text = clientSecret
        dialogView.findViewById<TextView>(R.id.secretWarningText).setText(warningRes)

        dialogView.findViewById<TextView>(R.id.copyClientIdButton).setOnClickListener {
            copyText(clientId, CLIP_LABEL_ID)
        }
        dialogView.findViewById<TextView>(R.id.copyClientSecretButton).setOnClickListener {
            copyText(clientSecret, CLIP_LABEL_SECRET)
        }
        dialogView.findViewById<TextView>(R.id.copyCurlButton).setOnClickListener {
            copyText(buildCurlExample(clientId, clientSecret), CLIP_LABEL_CURL)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_close, null)
            .setCancelable(false)
            .show()
    }

    private fun buildCurlExample(clientId: String, clientSecret: String): String =
        "curl https://your-app.example.com \\\n" +
            "  -H \"CF-Access-Client-Id: $clientId\" \\\n" +
            "  -H \"CF-Access-Client-Secret: $clientSecret\""

    private fun humanDuration(duration: String?): String = when (duration) {
        null, "" -> duration ?: ""
        ServiceTokenAdapter.DURATION_FOREVER -> getString(R.string.status_never_expires)
        "720h" -> getString(R.string.zt_service_token_duration_30d)
        "2160h" -> getString(R.string.zt_service_token_duration_90d)
        "4320h" -> getString(R.string.zt_service_token_duration_180d)
        "8760h" -> getString(R.string.zt_service_token_duration_1y)
        else -> duration
    }

    private fun copyText(text: String, label: String) {
        if (text.isEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), R.string.msg_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class DurationSelection(var index: Int)

    companion object {
        private const val CLIP_LABEL_ID = "CF-Access-Client-Id"
        private const val CLIP_LABEL_SECRET = "CF-Access-Client-Secret"
        private const val CLIP_LABEL_CURL = "Service Token cURL"

        private const val DURATION_DEFAULT_INDEX = 3

        private const val MENU_DETAIL = 1
        private const val MENU_REFRESH = 2
        private const val MENU_ROTATE = 3
        private const val MENU_EDIT = 4
        private const val MENU_TOGGLE_ENABLED = 5
        private const val MENU_DELETE = 6

        private const val GRACE_24H = 1
        private const val GRACE_7D = 2

        /** (label res, API duration value in hours) */
        private val durationOptions: List<Pair<Int, String>> = listOf(
            R.string.zt_service_token_duration_30d to "720h",
            R.string.zt_service_token_duration_90d to "2160h",
            R.string.zt_service_token_duration_180d to "4320h",
            R.string.zt_service_token_duration_1y to "8760h",
            R.string.status_never_expires to ServiceTokenAdapter.DURATION_FOREVER
        )
    }
}
