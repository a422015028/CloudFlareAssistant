package com.muort.upworker.feature.zone

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Snippet
import com.muort.upworker.core.repository.SnippetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SnippetsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<Snippet> = emptyList()

    override val emptyTextResId: Int = R.string.snippet_empty_list
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { _, item ->
                navigateToEditor(item.id, isNew = false)
            },
            onDelete = { _, item ->
                account?.let { deleteSnippet(it, item.id) }
            },
        )
        binding.recyclerView.adapter = adapter
        binding.addFab.setOnLongClickListener {
            showDeleteAllDialog()
            true
        }
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() = showAddNameDialog()

    private fun showAddNameDialog() {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (density * 24).toInt(),
                (density * 16).toInt(),
                (density * 24).toInt(),
                (density * 8).toInt()
            )
        }

        // ===== Material 3 OutlinedBox 样式：TextInputLayout(Outlined) + TextInputEditText =====
        val inputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = getString(R.string.snippet_name_hint)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            helperText = getString(R.string.msg_name_invalid)
            isHelperTextEnabled = true
        }
        val nameEdit = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_NORMAL or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val padStart  = (density * 16).toInt()
            val padTop    = (density * 14).toInt()
            val padEnd    = (density * 16).toInt()
            val padBottom = (density * 14).toInt()
            setPaddingRelative(padStart, padTop, padEnd, padBottom)
            minHeight = (density * 52).toInt()
        }
        inputLayout.addView(nameEdit)
        container.addView(inputLayout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.snippet_new_title)
            .setView(container)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener { iface ->
            val dlg = iface as androidx.appcompat.app.AlertDialog
            // 弹起后立刻聚焦输入框并显示键盘
            nameEdit.requestFocus()
            (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(nameEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameEdit.text?.toString()?.trim().orEmpty()
                when {
                    name.isEmpty() -> {
                        inputLayout.error = getString(R.string.msg_name_empty)
                        inputLayout.requestFocus()
                    }
                    !name.matches(Regex("^[a-z0-9_]+$")) -> {
                        inputLayout.error = getString(R.string.msg_name_invalid)
                        inputLayout.requestFocus()
                    }
                    else -> {
                        inputLayout.error = null
                        navigateToEditor(name, isNew = true)
                        dlg.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun navigateToEditor(snippetName: String, isNew: Boolean) {
        val action = SnippetsFragmentDirections.actionSnippetsToEditor(
            zoneId = zoneId,
            zoneName = zoneName,
            snippetName = snippetName,
            isNew = isNew,
        )
        findNavController().navigate(action)
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            val snippets = when (val r = snippetRepo.listSnippets(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> { showError(r.message); return@launch }
                is Resource.Loading -> return@launch
            }
            // 规则读取失败不影响片段列表，仅不展示规则状态
            val rules = when (val r = snippetRepo.listSnippetRules(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> emptyList()
                is Resource.Loading -> emptyList()
            }
            loaded = snippets
            val ctx = requireContext()
            val items = snippets.map { s ->
                s.toZoneRuleItem(ctx, rules.firstOrNull { it.snippetName == s.snippetName })
            }
            if (items.isEmpty()) showEmpty() else { showList(); adapter.submitList(items) }
        }
    }

    private fun deleteSnippet(account: Account, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.teardownSnippet(account, zoneId, name)) {
                is Resource.Success -> {
                    toast(getString(R.string.msg_deleted_with_rules)); load(account)
                }
                is Resource.Error -> toast(getString(R.string.msg_delete_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    private fun showDeleteAllDialog() {
        if (loaded.isEmpty()) {
            toast(getString(R.string.snippet_no_snippets))
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.snippet_clear_all_title)
            .setMessage(getString(R.string.snippet_clear_all_message, loaded.size))
            .setPositiveButton(R.string.snippet_clear_all_button) { _, _ -> deleteAllSnippets() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAllSnippets() {
        val acct = account ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            var ok = 0
            loaded.forEach { snippet ->
                val r = snippetRepo.teardownSnippet(acct, zoneId, snippet.snippetName)
                if (r is Resource.Success) ok++
            }
            toast(getString(R.string.snippet_bulk_delete_progress, ok, loaded.size))
            load(acct)
        }
    }

    private fun Snippet.toZoneRuleItem(
        ctx: Context,
        rule: com.muort.upworker.core.model.SnippetRule?,
    ): ZoneRuleItem = ZoneRuleItem(
        id = snippetName,
        title = snippetName,
        subtitle = getString(
            R.string.snippet_subtitle_created_modified,
            createdOn?.take(10) ?: "-",
            modifiedOn?.take(10) ?: "-",
        ),
        meta = when {
            rule == null -> getString(R.string.snippet_no_rule_attached)
            rule.enabled == false -> getString(R.string.snippet_rule_disabled, ruleSummary(ctx, rule))
            else -> getString(R.string.snippet_rule_label_format, ruleSummary(ctx, rule))
        },
        enabled = null,
        canDelete = true,
    )

    private fun ruleSummary(ctx: Context, rule: com.muort.upworker.core.model.SnippetRule): String =
        if (rule.expression.trim() == "true") ctx.getString(R.string.snippet_all_requests_label)
        else rule.expression.let { if (it.length > 60) it.take(60) + "…" else it }
}
