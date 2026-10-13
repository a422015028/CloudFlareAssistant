package com.muort.upworker.feature.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.SecretQuota
import com.muort.upworker.core.model.SecretScope
import com.muort.upworker.core.model.SecretStore
import com.muort.upworker.core.model.StoredSecret
import com.muort.upworker.core.model.StoredSecretCreateBody
import com.muort.upworker.core.model.StoredSecretDuplicateBody
import com.muort.upworker.core.util.notifyListChanged
import com.muort.upworker.databinding.DialogSecretStoreInputBinding
import com.muort.upworker.databinding.DialogStoredSecretFormBinding
import com.muort.upworker.databinding.ItemSecretStoreBinding
import com.muort.upworker.databinding.ItemStoredSecretBinding
import com.muort.upworker.databinding.ViewSecretsStoreBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Secrets Store 两级管理面板（密钥库列表 → 库内密钥）。
 * 作为第三个 Tab 嵌入 [TokenManagerActivity]，复用 Activity 级
 * [SecretsStoreViewModel] 与当前账号。
 */
class SecretsStorePanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewSecretsStoreBinding.inflate(LayoutInflater.from(context), this)
    private val viewModel: SecretsStoreViewModel by lazy {
        ViewModelProvider(context as AppCompatActivity)[SecretsStoreViewModel::class.java]
    }

    private var activeAccount: Account? = null
    private var lastAccountId: String? = null
    private var observed = false
    private var searchJob: Job? = null
    private var secretsCount = 0

    private lateinit var storesAdapter: SecretStoreAdapter
    private lateinit var secretsAdapter: StoredSecretAdapter

    /** scope 枚举 → 过滤 chip 实例 */
    private val scopeFilterChips = mutableMapOf<String, Chip>()

    init {
        orientation = VERTICAL
        setupUi()
    }

    // ==================== 对外接口 ====================

    /** 切到 Secrets Store Tab：确保按当前账号加载 */
    fun show(account: Account) {
        visibility = VISIBLE
        activeAccount = account
        viewModel.bindAccount(account)
        if (lastAccountId != null && lastAccountId != account.accountId) {
            // 切换账号后回到密钥库一级
            viewModel.backToStores()
        }
        lastAccountId = account.accountId
        viewModel.loadStores(account)
    }

    fun hide() {
        visibility = GONE
    }

    fun isShowing(): Boolean = visibility == VISIBLE

    fun refresh() {
        activeAccount?.let { viewModel.refresh(it) }
    }

    /** 处理返回键：密钥二级先返回密钥库一级 */
    fun handleBack(): Boolean {
        if (visibility != VISIBLE) return false
        return if (viewModel.selectedStore.value != null) {
            viewModel.backToStores()
            true
        } else {
            false
        }
    }

    // ==================== 初始化 ====================

    private fun setupUi() {
        storesAdapter = SecretStoreAdapter(
            onOpen = { store -> viewModel.openStore(store) },
            onDelete = { store -> showStoreDeleteDialog(store) }
        )
        binding.storesRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.storesRecyclerView.adapter = storesAdapter

        secretsAdapter = StoredSecretAdapter(
            onDetail = { secret -> showSecretDetailDialog(secret) },
            onEdit = { secret -> showSecretFormDialog(FormMode.EDIT, secret) },
            onDuplicate = { secret -> showSecretFormDialog(FormMode.DUPLICATE, secret) },
            onDelete = { secret -> showSecretDeleteDialog(secret) },
            onCopyName = { secret -> copyText("secret_name", secret.name) },
            onCopyId = { secret -> copyText("secret_id", secret.id) }
        )
        binding.secretsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.secretsRecyclerView.adapter = secretsAdapter

        binding.createStoreBtn.setOnClickListener { showStoreCreateDialog() }
        binding.createSecretBtn.setOnClickListener {
            showSecretFormDialog(FormMode.CREATE, null)
        }
        binding.backToStoresBtn.setOnClickListener { viewModel.backToStores() }
        binding.clearSecretsBtn.setOnClickListener { showBulkDeleteDialog() }
        binding.storesRetryBtn.setOnClickListener {
            activeAccount?.let { viewModel.loadStores(it, force = true) }
        }
        binding.secretsRetryBtn.setOnClickListener {
            activeAccount?.let { acc -> viewModel.selectedStore.value?.let { viewModel.loadSecrets(acc, it) } }
        }

        setupScopeFilterChips()
        binding.secretSearchInput.addTextChangedListener(object : SimpleTextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                val owner = findViewTreeLifecycleOwner()
                val q = s?.toString().orEmpty()
                if (owner != null) {
                    searchJob = owner.lifecycleScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        viewModel.setSearchQuery(q)
                    }
                }
            }
        })
    }

    private fun inflateFilterChip(parent: ChipGroup, text: String, checked: Boolean = false): Chip {
        val chip = LayoutInflater.from(context)
            .inflate(R.layout.item_ss_filter_chip, parent, false) as Chip
        chip.text = text
        chip.isChecked = checked
        return chip
    }

    private fun setupScopeFilterChips() {
        val group = binding.scopeFilterChipGroup
        group.removeAllViews()
        scopeFilterChips.clear()
        val allChip = inflateFilterChip(group, getString(R.string.ss_filter_all), checked = true)
        group.addView(allChip)
        SecretScope.ALL.forEach { scope ->
            val chip = inflateFilterChip(group, scopeLabel(scope))
            scopeFilterChips[scope] = chip
            group.addView(chip)
        }
        bindScopeFilterListener(group, allChip)
    }

    private fun bindScopeFilterListener(group: ChipGroup, allChip: Chip) {
        group.setOnCheckedStateChangeListener { g, checkedIds ->
            if (checkedIds.isEmpty()) {
                // 单选组允许取消选中：空选等同于“全部”，并把“全部服务”补选中
                allChip.post { if (g.checkedChipIds.isEmpty()) allChip.isChecked = true }
                viewModel.setScopeFilter(null)
                return@setOnCheckedStateChangeListener
            }
            val checkedId = checkedIds.first()
            if (checkedId == allChip.id) {
                viewModel.setScopeFilter(null)
            } else {
                val scope = scopeFilterChips.entries.firstOrNull { it.value.id == checkedId }?.key
                viewModel.setScopeFilter(scope)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (observed) return
        val owner = findViewTreeLifecycleOwner() ?: return
        observed = true
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.storesState.collect { renderStores(it) } }
                launch { viewModel.secretsState.collect { renderSecrets(it) } }
                launch {
                    viewModel.selectedStore.collect { store -> renderLevel(store) }
                }
                launch { viewModel.quota.collect { renderQuota(it) } }
                launch { viewModel.message.collect { toast(it.asString(context)) } }
                launch {
                    viewModel.forceDeleteStorePrompt.collect { store -> showForceDeleteDialog(store) }
                }
            }
        }
    }

    // ==================== 渲染 ====================

    private fun renderLevel(store: SecretStore?) {
        if (store == null) {
            binding.storesLevel.visibility = VISIBLE
            binding.secretsLevel.visibility = GONE
        } else {
            binding.storesLevel.visibility = GONE
            binding.secretsLevel.visibility = VISIBLE
            binding.secretsStoreTitle.text = store.name
            // 重置搜索与过滤
            binding.secretSearchInput.setText("")
            resetFilterChips()
            viewModel.setSearchQuery("")
            viewModel.setScopeFilter(null)
        }
    }

    private fun resetFilterChips() {
        val group = binding.scopeFilterChipGroup
        group.setOnCheckedStateChangeListener(null)
        group.clearCheck()
        val allChip = (group.getChildAt(0) as? Chip)
        allChip?.isChecked = true
        if (allChip != null) bindScopeFilterListener(group, allChip)
    }

    private fun renderStores(state: SecretsStoreUiState) {
        binding.storesRecyclerView.visibility = if (state is SecretsStoreUiState.Content) VISIBLE else GONE
        binding.storesProgressBar.visibility = if (state is SecretsStoreUiState.Loading) VISIBLE else GONE
        binding.storesEmptyLayout.visibility = if (state is SecretsStoreUiState.Empty) VISIBLE else GONE
        binding.storesErrorLayout.visibility = if (state is SecretsStoreUiState.Error) VISIBLE else GONE
        when (state) {
            is SecretsStoreUiState.Content -> storesAdapter.submitList(state.stores)
            is SecretsStoreUiState.Error -> binding.storesErrorText.text = state.message.asString(context)
            else -> storesAdapter.submitList(emptyList())
        }
    }

    private fun renderSecrets(state: StoredSecretUiState) {
        binding.secretsRecyclerView.visibility = if (state is StoredSecretUiState.Content) VISIBLE else GONE
        binding.secretsProgressBar.visibility = if (state is StoredSecretUiState.Loading) VISIBLE else GONE
        binding.secretsEmptyLayout.visibility = if (state is StoredSecretUiState.Empty) VISIBLE else GONE
        binding.secretsErrorLayout.visibility = if (state is StoredSecretUiState.Error) VISIBLE else GONE

        secretsCount = (state as? StoredSecretUiState.Content)?.secrets?.size ?: 0
        binding.clearSecretsBtn.isEnabled = secretsCount > 0

        if (state is StoredSecretUiState.Empty) {
            val filtering = viewModel.filterActive.value
            binding.secretsEmptyTitle.setText(
                if (filtering) R.string.ss_secret_filter_empty else R.string.ss_secret_empty
            )
            binding.secretsEmptyHint.setText(
                if (filtering) R.string.ss_secret_filter_empty_hint else R.string.ss_secret_empty_hint
            )
        }
        when (state) {
            is StoredSecretUiState.Content -> secretsAdapter.submitList(state.secrets)
            is StoredSecretUiState.Error -> binding.secretsErrorText.text = state.message.asString(context)
            else -> secretsAdapter.submitList(emptyList())
        }
    }

    private fun renderQuota(quota: SecretQuota?) {
        binding.quotaText.visibility = if (quota != null) VISIBLE else GONE
        if (quota != null) {
            binding.quotaText.text = getString(R.string.ss_quota_format, quota.usage, quota.quota)
        }
    }

    // ==================== 密钥库对话框 ====================

    private fun showStoreCreateDialog() {
        val account = activeAccount ?: return toast(getString(R.string.msg_please_select_account_first))
        val dialogBinding = DialogSecretStoreInputBinding.inflate(LayoutInflater.from(context))
        dialogBinding.dialogTitle.setText(R.string.ss_create_store)
        MaterialAlertDialogBuilder(context)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = dialogBinding.storeNameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(getString(R.string.ss_store_name_empty))
                    return@setPositiveButton
                }
                viewModel.createStore(account, name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showStoreDeleteDialog(store: SecretStore) {
        val account = activeAccount ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.ss_store_delete_title))
            .setMessage(getString(R.string.ss_store_delete_message, store.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteStore(account, store, force = false) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 非空库普通删除被拒后，引导级联强制删除 */
    private fun showForceDeleteDialog(store: SecretStore) {
        val account = activeAccount ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.ss_store_delete_force_title))
            .setMessage(getString(R.string.ss_store_delete_force_message, store.name))
            .setPositiveButton(R.string.ss_store_delete_force_btn) { _, _ ->
                viewModel.deleteStore(account, store, force = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 密钥对话框 ====================

    private enum class FormMode { CREATE, EDIT, DUPLICATE }

    private fun showSecretFormDialog(mode: FormMode, existing: StoredSecret?) {
        val account = activeAccount ?: return
        val store = viewModel.selectedStore.value ?: return
        val form = DialogStoredSecretFormBinding.inflate(LayoutInflater.from(context))

        // 标题
        form.formTitle.setText(
            when (mode) {
                FormMode.CREATE -> R.string.ss_create_secret
                FormMode.EDIT -> R.string.ss_edit_secret
                FormMode.DUPLICATE -> R.string.ss_duplicate_title
            }
        )

        // scope 多选 chips
        val scopeChips = mutableMapOf<String, Chip>()
        SecretScope.ALL.forEach { scope ->
            val chip = inflateFilterChip(form.scopesChipGroup, scopeLabel(scope))
            form.scopesChipGroup.addView(chip)
            scopeChips[scope] = chip
        }

        when (mode) {
            FormMode.CREATE -> {
                form.secretValueLayout.setHint(R.string.ss_secret_value_hint)
                form.secretValueHint.setText(R.string.ss_value_writeonly_hint)
            }
            FormMode.EDIT -> {
                form.secretNameInput.setText(existing?.name)
                form.secretNameInput.isEnabled = false
                form.secretNameLayout.isEnabled = false
                form.secretValueLayout.setHint(R.string.ss_value_edit_hint)
                form.secretValueHint.setText(R.string.ss_value_edit_hint_sub)
                form.secretCommentInput.setText(existing?.comment.orEmpty())
                existing?.scopes?.forEach { scopeChips[it]?.isChecked = true }
            }
            FormMode.DUPLICATE -> {
                form.secretNameInput.setText(existing?.name?.let { "$it${COPY_SUFFIX}" })
                form.secretValueLayout.visibility = GONE
                form.secretValueHint.visibility = GONE
                form.secretCommentInput.setText(existing?.comment.orEmpty())
                existing?.scopes?.forEach { scopeChips[it]?.isChecked = true }
            }
        }

        MaterialAlertDialogBuilder(context)
            .setView(form.root)
            .setPositiveButton(
                when (mode) {
                    FormMode.CREATE -> R.string.dialog_create
                    FormMode.EDIT -> R.string.save
                    FormMode.DUPLICATE -> R.string.ss_duplicate_btn
                }
            ) { _, _ ->
                val name = form.secretNameInput.text?.toString()?.trim().orEmpty()
                val value = form.secretValueInput.text?.toString().orEmpty()
                val comment = form.secretCommentInput.text?.toString()?.trim().orEmpty()
                val selectedScopes = scopeChips.filterValues { it.isChecked }.keys.toList()

                if (name.isEmpty()) {
                    toast(getString(R.string.ss_secret_name_empty)); return@setPositiveButton
                }
                if (mode == FormMode.CREATE && value.isEmpty()) {
                    toast(getString(R.string.ss_secret_value_empty)); return@setPositiveButton
                }
                if (selectedScopes.isEmpty()) {
                    toast(getString(R.string.ss_secret_scope_empty)); return@setPositiveButton
                }
                if (value.isNotEmpty() && value.toByteArray(Charsets.UTF_8).size > SecretScope.MAX_VALUE_BYTES) {
                    toast(getString(R.string.ss_secret_value_too_large, SecretScope.MAX_VALUE_BYTES))
                    return@setPositiveButton
                }

                when (mode) {
                    FormMode.CREATE -> viewModel.createSecret(
                        account, store.id,
                        StoredSecretCreateBody(name, selectedScopes, value, comment.ifBlank { null })
                    )
                    FormMode.EDIT -> viewModel.editSecret(
                        account, store.id, existing!!.id,
                        comment, selectedScopes, value.ifEmpty { null }
                    )
                    FormMode.DUPLICATE -> viewModel.duplicateSecret(
                        account, existing!!,
                        StoredSecretDuplicateBody(name, selectedScopes, comment.ifBlank { null })
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSecretDetailDialog(secret: StoredSecret) {
        val scopeNames = secret.scopes?.joinToString(", ") { scopeLabel(it) }
            ?: getString(R.string.status_none)
        val content = buildString {
            appendLine(getString(R.string.ss_detail_name, secret.name))
            appendLine(getString(R.string.ss_detail_id, secret.id))
            appendLine(getString(R.string.ss_detail_status, statusDisplay(secret.status)))
            appendLine(getString(R.string.ss_detail_scopes, scopeNames))
            appendLine(getString(R.string.ss_detail_comment, secret.comment ?: getString(R.string.status_none)))
            appendLine()
            appendLine(getString(R.string.ss_detail_created, formatIsoTime(secret.created)))
            appendLine(getString(R.string.ss_detail_modified, formatIsoTime(secret.modified)))
            appendLine()
            appendLine(getString(R.string.ss_detail_value_writeonly))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.ss_detail_title)
            .setMessage(content)
            .setPositiveButton(R.string.dialog_close, null)
            .show()
    }

    private fun showSecretDeleteDialog(secret: StoredSecret) {
        val account = activeAccount ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.ss_secret_delete_title))
            .setMessage(getString(R.string.ss_secret_delete_message, secret.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteSecret(account, secret) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showBulkDeleteDialog() {
        val account = activeAccount ?: return
        val store = viewModel.selectedStore.value ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.ss_bulk_delete_title))
            .setMessage(getString(R.string.ss_bulk_delete_message, store.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.bulkDeleteSecrets(account, store) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 工具 ====================

    private fun scopeLabel(scope: String): String = when (scope) {
        SecretScope.WORKERS -> getString(R.string.ss_scope_workers)
        SecretScope.AI_GATEWAY -> getString(R.string.ss_scope_ai_gateway)
        SecretScope.DEX -> getString(R.string.ss_scope_dex)
        SecretScope.ACCESS -> getString(R.string.ss_scope_access)
        SecretScope.CONTAINERS -> getString(R.string.ss_scope_containers)
        SecretScope.WEBSEARCH -> getString(R.string.ss_scope_websearch)
        else -> scope
    }

    private fun statusDisplay(status: String?): String = when (status) {
        "active" -> getString(R.string.r2_status_active)
        "pending" -> getString(R.string.ss_status_pending)
        "deleted" -> getString(R.string.ss_status_deleted)
        else -> status ?: getString(R.string.status_unknown)
    }

    private fun formatIsoTime(iso: String?): String {
        if (iso.isNullOrBlank()) return getString(R.string.status_none)
        return iso.take(16).replace("T", " ") + " UTC"
    }

    private fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(getString(R.string.msg_copied_to_clipboard))
    }

    private fun getString(resId: Int): String = context.getString(resId)

    private fun getString(resId: Int, vararg args: Any?): String =
        @Suppress("SpreadOperator") context.getString(resId, *args)

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
        private const val COPY_SUFFIX = "_copy"
    }
}

// ==================== Adapters ====================

private interface SimpleTextWatcher : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {}
}

class SecretStoreAdapter(
    private val onOpen: (SecretStore) -> Unit,
    private val onDelete: (SecretStore) -> Unit
) : RecyclerView.Adapter<SecretStoreAdapter.StoreViewHolder>() {

    private var stores = listOf<SecretStore>()

    fun submitList(list: List<SecretStore>) {
        val old = stores.size
        stores = list
        notifyListChanged(old, stores.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val binding = ItemSecretStoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) = holder.bind(stores[position])

    override fun getItemCount() = stores.size

    inner class StoreViewHolder(private val b: ItemSecretStoreBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(store: SecretStore) {
            val ctx = b.root.context
            b.storeNameText.text = store.name
            b.storeIdText.text = store.id
            b.storeTimeText.text = buildString {
                append(ctx.getString(R.string.ss_created_short, store.created?.take(10) ?: ctx.getString(R.string.status_none)))
                append("  ·  ")
                append(ctx.getString(R.string.ss_modified_short, store.modified?.take(10) ?: ctx.getString(R.string.status_none)))
            }
            b.root.setOnClickListener { onOpen(store) }
            b.storeOpenBtn.setOnClickListener { onOpen(store) }
            b.storeDeleteBtn.setOnClickListener { onDelete(store) }
        }
    }
}

class StoredSecretAdapter(
    private val onDetail: (StoredSecret) -> Unit,
    private val onEdit: (StoredSecret) -> Unit,
    private val onDuplicate: (StoredSecret) -> Unit,
    private val onDelete: (StoredSecret) -> Unit,
    private val onCopyName: (StoredSecret) -> Unit,
    private val onCopyId: (StoredSecret) -> Unit
) : RecyclerView.Adapter<StoredSecretAdapter.SecretViewHolder>() {

    private var secrets = listOf<StoredSecret>()

    fun submitList(list: List<StoredSecret>) {
        val old = secrets.size
        secrets = list
        notifyListChanged(old, secrets.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretViewHolder {
        val binding = ItemStoredSecretBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SecretViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SecretViewHolder, position: Int) = holder.bind(secrets[position])

    override fun getItemCount() = secrets.size

    inner class SecretViewHolder(private val b: ItemStoredSecretBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(secret: StoredSecret) {
            val ctx = b.root.context
            b.secretNameText.text = secret.name
            b.secretIdText.text = secret.id
            b.secretStatusChip.text = when (secret.status) {
                "active" -> ctx.getString(R.string.r2_status_active)
                "pending" -> ctx.getString(R.string.ss_status_pending)
                "deleted" -> ctx.getString(R.string.ss_status_deleted)
                else -> secret.status ?: ctx.getString(R.string.status_unknown)
            }
            b.secretStatusChip.chipBackgroundColor = ColorStateList.valueOf(0x00000000)
            b.secretStatusChip.setTextColor(
                ctx.getColor(
                    when (secret.status) {
                        "active" -> android.R.color.holo_green_dark
                        "pending" -> android.R.color.holo_orange_dark
                        "deleted" -> R.color.md_theme_error
                        else -> android.R.color.darker_gray
                    }
                )
            )

            if (!secret.comment.isNullOrBlank()) {
                b.secretCommentText.visibility = View.VISIBLE
                b.secretCommentText.text = secret.comment
            } else {
                b.secretCommentText.visibility = View.GONE
            }

            renderScopeBadges(secret.scopes)

            b.secretTimeText.text = buildString {
                append(ctx.getString(R.string.ss_created_short, secret.created?.take(10) ?: ctx.getString(R.string.status_none)))
                append("  ·  ")
                append(ctx.getString(R.string.ss_modified_short, secret.modified?.take(10) ?: ctx.getString(R.string.status_none)))
            }

            b.secretDetailBtn.setOnClickListener { onDetail(secret) }
            b.secretEditBtn.setOnClickListener { onEdit(secret) }
            b.secretDuplicateBtn.setOnClickListener { onDuplicate(secret) }
            b.secretDeleteBtn.setOnClickListener { onDelete(secret) }
            b.root.setOnLongClickListener {
                PopupMenu(ctx, b.root).apply {
                    menu.add(ctx.getString(R.string.ss_copy_name)).setOnMenuItemClickListener {
                        onCopyName(secret); true
                    }
                    menu.add(ctx.getString(R.string.ss_copy_id)).setOnMenuItemClickListener {
                        onCopyId(secret); true
                    }
                    show()
                }
                true
            }
        }

        private fun renderScopeBadges(scopes: List<String>?) {
            val container = b.secretScopeBadges
            container.removeAllViews()
            val list = scopes.orEmpty()
            if (list.isEmpty()) return
            val ctx = container.context
            list.forEach { scope ->
                container.addView(makeBadge(ctx, scopeLabelText(ctx, scope), scopeColor(scope)))
            }
        }

        private fun scopeLabelText(ctx: android.content.Context, scope: String): String = when (scope) {
            SecretScope.WORKERS -> ctx.getString(R.string.ss_scope_workers)
            SecretScope.AI_GATEWAY -> ctx.getString(R.string.ss_scope_ai_gateway)
            SecretScope.DEX -> ctx.getString(R.string.ss_scope_dex)
            SecretScope.ACCESS -> ctx.getString(R.string.ss_scope_access)
            SecretScope.CONTAINERS -> ctx.getString(R.string.ss_scope_containers)
            SecretScope.WEBSEARCH -> ctx.getString(R.string.ss_scope_websearch)
            else -> scope
        }

        private fun scopeColor(scope: String): Int = when (scope) {
            SecretScope.WORKERS -> 0xFF1E88E5.toInt()
            SecretScope.AI_GATEWAY -> 0xFF8E24AA.toInt()
            SecretScope.DEX -> 0xFF00897B.toInt()
            SecretScope.ACCESS -> 0xFF43A047.toInt()
            SecretScope.CONTAINERS -> 0xFFFB8C00.toInt()
            SecretScope.WEBSEARCH -> 0xFFE53935.toInt()
            else -> 0xFF757575.toInt()
        }

        private fun makeBadge(context: Context, text: String, color: Int): TextView {
            val density = context.resources.displayMetrics.density
            return TextView(context).apply {
                this.text = text
                textSize = 10f
                setTextColor(color)
                setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 10f * density
                    setStroke(maxOf(1, density.toInt()), (color and 0x00FFFFFF) or 0x66000000)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (6 * density).toInt() }
            }
        }
    }
}
