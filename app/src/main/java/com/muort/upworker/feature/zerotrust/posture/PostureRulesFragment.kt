package com.muort.upworker.feature.zerotrust.posture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonParser
import com.muort.upworker.R
import com.muort.upworker.core.model.DevicePostureRule
import com.muort.upworker.core.model.DevicePostureRuleRequest
import com.muort.upworker.core.model.PosturePlatformMatch
import com.muort.upworker.databinding.FragmentPostureRulesBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PostureRulesFragment : Fragment() {

    private var _binding: FragmentPostureRulesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicePostureViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var ruleAdapter: PostureRuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostureRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ruleAdapter = PostureRuleAdapter(
            onCardClick = { rule -> showDetailDialog(rule) },
            onEditClick = { rule -> showEditDialog(rule) },
            onDeleteClick = { rule -> confirmDelete(rule) }
        )
        binding.postureRulesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ruleAdapter
        }
        binding.createPostureRuleButton.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rules.collect { rules ->
                    ruleAdapter.submitList(rules)
                    binding.postureRulesEmptyView.visibility =
                        if (rules.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ==================== Create ====================

    private fun showCreateDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_posture_rule_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.ruleNameInput)
        val typeDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleTypeDropdown)
        val scheduleDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleScheduleDropdown)
        val inputJson = dialogView.findViewById<TextInputEditText>(R.id.ruleInputJson)
        val integrationLayout = dialogView.findViewById<View>(R.id.ruleIntegrationLayout)
        val integrationDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleIntegrationDropdown)

        setupPlatformChips(dialogView.findViewById(R.id.rulePlatformChips), selectedPlatforms = emptySet())
        setupTypeDropdown(typeDropdown, inputJson, initialType = PostureTypeSpecs.ruleTypes.first().type,
            platformChips = dialogView.findViewById(R.id.rulePlatformChips),
            integrationLayout = integrationLayout,
            integrationDropdown = integrationDropdown)
        val scheduleSelection = setupScheduleDropdown(scheduleDropdown, "5m")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_rule_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(R.string.zt_posture_rule_name_empty)
                    return@setPositiveButton
                }

                val input = parseJsonObject(inputJson.text?.toString()) ?: run {
                    toast(R.string.zt_posture_rule_input_invalid)
                    return@setPositiveButton
                }

                viewModel.createRule(
                    account,
                    DevicePostureRuleRequest(
                        name = name,
                        type = typeDropdown.tag as String,
                        description = dialogView.findViewById<TextInputEditText>(R.id.ruleDescriptionInput)
                            .text?.toString()?.trim()?.ifEmpty { null },
                        schedule = scheduleSelection.value,
                        match = selectedPlatforms(dialogView.findViewById(R.id.rulePlatformChips)),
                        input = input
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Edit ====================

    private fun showEditDialog(rule: DevicePostureRule) {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_posture_rule_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.ruleNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.ruleDescriptionInput)
        val typeDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleTypeDropdown)
        val scheduleDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleScheduleDropdown)
        val inputJson = dialogView.findViewById<TextInputEditText>(R.id.ruleInputJson)

        nameInput.setText(rule.name.orEmpty())
        descriptionInput.setText(rule.description.orEmpty())
        val rawJson = rule.input?.toString() ?: PostureTypeSpecs.ruleTemplate(rule.type.orEmpty())
        inputJson.setText(prettyJson(rawJson))

        val integrationLayout = dialogView.findViewById<View>(R.id.ruleIntegrationLayout)
        val integrationDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.ruleIntegrationDropdown)

        // Editing: keep the existing type; changing it is intentionally avoided
        // because input shapes are incompatible across types.
        setupPlatformChips(
            dialogView.findViewById(R.id.rulePlatformChips),
            selectedPlatforms = rule.match.orEmpty().map { it.platform }.toSet()
        )
        setupTypeDropdown(typeDropdown, inputJson, initialType = rule.type ?: PostureTypeSpecs.ruleTypes.first().type, locked = true,
            platformChips = dialogView.findViewById(R.id.rulePlatformChips),
            integrationLayout = integrationLayout,
            integrationDropdown = integrationDropdown)

        // Pre-select matching integration if rule has a connection_id
        val existingConnectionId = rule.input?.get("connection_id")?.asString
        if (existingConnectionId != null) {
            val matching = viewModel.integrations.value.firstOrNull { it.id == existingConnectionId }
            if (matching != null) {
                integrationDropdown.setText(matching.name ?: matching.id.orEmpty(), false)
                integrationDropdown.tag = matching.id
            }
        }

        val scheduleSelection = setupScheduleDropdown(scheduleDropdown, rule.schedule ?: "5m")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_rule_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(R.string.zt_posture_rule_name_empty)
                    return@setPositiveButton
                }

                val input = parseJsonObject(inputJson.text?.toString()) ?: run {
                    toast(R.string.zt_posture_rule_input_invalid)
                    return@setPositiveButton
                }

                viewModel.updateRule(
                    account,
                    rule.id!!,
                    DevicePostureRuleRequest(
                        name = name,
                        type = rule.type!!,
                        description = descriptionInput.text?.toString()?.trim()?.ifEmpty { null },
                        schedule = scheduleSelection.value,
                        match = selectedPlatforms(dialogView.findViewById(R.id.rulePlatformChips)),
                        input = input
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Detail ====================

    private fun showDetailDialog(rule: DevicePostureRule) {
        val ctx = requireContext()
        val message = buildString {
            append(ctx.getString(R.string.zt_posture_detail_type, ctx.getString(PostureTypeSpecs.ruleLabel(rule.type))))
            append("\n")
            val platforms = rule.match.orEmpty().mapNotNull { m ->
                PostureTypeSpecs.platforms.firstOrNull { it.platform == m.platform }
                    ?.let { ctx.getString(it.labelRes) }
            }.joinToString(", ").ifEmpty { ctx.getString(R.string.zt_posture_platforms_all) }
            append(ctx.getString(R.string.zt_posture_platforms_label, platforms))
            rule.schedule?.takeIf { it.isNotBlank() }?.let {
                append("\n").append(ctx.getString(R.string.zt_posture_schedule_label, it))
            }
            rule.description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n").append(it)
            }
            append("\n\n")
            append(ctx.getString(R.string.zt_posture_rule_input_json))
            append(":\n")
            append(prettyJson(rule.input?.toString() ?: "{}"))
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(rule.name ?: ctx.getString(R.string.status_unknown))
            .setMessage(message)
            .setPositiveButton(R.string.edit) { _, _ -> showEditDialog(rule) }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun confirmDelete(rule: DevicePostureRule) {
        val account = accountViewModel.defaultAccount.value ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_rule_delete_title)
            .setMessage(getString(R.string.zt_posture_rule_delete_confirm, rule.name ?: rule.id.orEmpty()))
            .setPositiveButton(R.string.delete) { _, _ ->
                rule.id?.let { viewModel.deleteRule(account, it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Dialog helpers ====================

    private fun setupTypeDropdown(
        dropdown: android.widget.AutoCompleteTextView,
        inputJson: TextInputEditText,
        initialType: String,
        locked: Boolean = false,
        platformChips: ChipGroup? = null,
        integrationLayout: View? = null,
        integrationDropdown: android.widget.AutoCompleteTextView? = null
    ) {
        val labels = PostureTypeSpecs.ruleTypes.map { getString(it.labelRes) }
        dropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        val initialIndex = PostureTypeSpecs.ruleTypes.indexOfFirst { it.type == initialType }.coerceAtLeast(0)
        dropdown.setText(labels[initialIndex], false)
        dropdown.tag = PostureTypeSpecs.ruleTypes[initialIndex].type
        inputJson.setText(prettyJson(PostureTypeSpecs.ruleTemplate(initialType)))
        platformChips?.let { updatePlatformChipsForType(it, initialType) }
        if (integrationLayout != null && integrationDropdown != null) {
            updateIntegrationDropdownForType(integrationLayout, integrationDropdown, inputJson, initialType)
        }

        if (!locked) {
            dropdown.setOnItemClickListener { _, _, position, _ ->
                val spec = PostureTypeSpecs.ruleTypes[position]
                dropdown.tag = spec.type
                val merged = mergeJsonPreservingCommon(
                    inputJson.text?.toString(),
                    spec.inputTemplate
                )
                inputJson.setText(prettyJson(merged))
                platformChips?.let { updatePlatformChipsForType(it, spec.type) }
                if (integrationLayout != null && integrationDropdown != null) {
                    updateIntegrationDropdownForType(integrationLayout, integrationDropdown, inputJson, spec.type)
                }
            }
        }
    }

    /**
     * Show/hide the integration dropdown based on whether the rule type uses connection_id.
     * Populates the dropdown with matching integrations and sets up selection to auto-fill connection_id.
     */
    private fun updateIntegrationDropdownForType(
        integrationLayout: View,
        integrationDropdown: android.widget.AutoCompleteTextView,
        inputJson: TextInputEditText,
        ruleType: String
    ) {
        val spec = PostureTypeSpecs.ruleTypes.firstOrNull { it.type == ruleType }
        val integrationType = spec?.connectionIdIntegrationType
        if (integrationType == null) {
            integrationLayout.visibility = View.GONE
            integrationDropdown.text.clear()
            integrationDropdown.tag = null
            return
        }

        // Show dropdown with matching integrations
        integrationLayout.visibility = View.VISIBLE
        val matchingIntegrations = viewModel.integrations.value
            .filter { it.type == integrationType }
        val integrationLabels = matchingIntegrations.map { it.name ?: it.id.orEmpty() }
        integrationDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, integrationLabels)
        )
        integrationDropdown.setText("")
        integrationDropdown.tag = null

        integrationDropdown.setOnItemClickListener { _, _, position, _ ->
            if (position < matchingIntegrations.size) {
                val integration = matchingIntegrations[position]
                integrationDropdown.tag = integration.id
                // Update connection_id in the input JSON
                val currentJson = inputJson.text?.toString()
                val updated = setConnectionIdInJson(currentJson, integration.id.orEmpty())
                inputJson.setText(prettyJson(updated))
            }
        }
    }

    /** Returns a JSON string with the connection_id field set to the given value. */
    private fun setConnectionIdInJson(raw: String?, connectionId: String): String {
        val obj = parseJsonObject(raw) ?: com.google.gson.JsonObject()
        obj.addProperty("connection_id", connectionId)
        return obj.toString()
    }

    private class StringSelection(var value: String?)

    private fun setupScheduleDropdown(dropdown: android.widget.AutoCompleteTextView, current: String): StringSelection {
        val selection = StringSelection(current)
        val options = PostureTypeSpecs.scheduleOptions
        dropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options))
        val index = options.indexOf(current).coerceAtLeast(1) // default 5m
        dropdown.setText(options[index], false)
        selection.value = options[index]
        dropdown.setOnItemClickListener { _, _, position, _ ->
            selection.value = options[position]
        }
        return selection
    }

    private fun setupPlatformChips(chipGroup: ChipGroup, selectedPlatforms: Set<String>) {
        PostureTypeSpecs.platforms.forEach { spec ->
            val chip = Chip(requireContext()).apply {
                text = getString(spec.labelRes)
                isCheckable = true
                isChecked = spec.platform in selectedPlatforms
                tag = spec.platform
            }
            chipGroup.addView(chip)
        }
    }

    /**
     * Update platform chips enabled state based on the selected rule type.
     * Chips for unsupported platforms are disabled and unchecked.
     */
    private fun updatePlatformChipsForType(chipGroup: ChipGroup, ruleType: String) {
        val spec = PostureTypeSpecs.ruleTypes.firstOrNull { it.type == ruleType } ?: return
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            val platform = chip.tag as String
            val supported = spec.supportsPlatform(platform)
            chip.isEnabled = supported
            if (!supported && chip.isChecked) {
                chip.isChecked = false
            }
        }
    }

    private fun selectedPlatforms(chipGroup: ChipGroup): List<PosturePlatformMatch>? {
        val platforms = chipGroup.checkedChipIds
            .map { (chipGroup.findViewById<Chip>(it).tag as String) }
        return platforms.takeIf { it.isNotEmpty() }?.map { PosturePlatformMatch(it) }
    }

    private fun parseJsonObject(raw: String?): com.google.gson.JsonObject? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching {
            val element = JsonParser.parseString(text)
            if (element.isJsonObject) element.asJsonObject else null
        }.getOrNull()
    }

    private fun prettyJson(raw: String): String =
        runCatching {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            gson.toJson(JsonParser.parseString(raw))
        }.getOrDefault(raw)

    /**
     * Merge the user's current JSON with a new template, preserving values
     * for keys that exist in both (same type). This avoids wiping out
     * user-entered values when switching between related rule types
     * (e.g. file ↔ application, or between two S2S providers).
     */
    private fun mergeJsonPreservingCommon(oldRaw: String?, templateRaw: String): String {
        val old = parseJsonObject(oldRaw) ?: return templateRaw
        val template = parseJsonObject(templateRaw) ?: return templateRaw
        val merged = com.google.gson.JsonObject()
        for ((key, templateValue) in template.entrySet()) {
            if (old.has(key)) {
                val oldValue = old[key]
                // Preserve only if both values are of the same primitive type
                // (both strings, both numbers, both booleans, both arrays of same kind)
                // to avoid type mismatches.
                if (oldValue.javaClass == templateValue.javaClass ||
                    (oldValue.isJsonPrimitive && templateValue.isJsonPrimitive &&
                        oldValue.asJsonPrimitive.isString == templateValue.asJsonPrimitive.isString &&
                        oldValue.asJsonPrimitive.isNumber == templateValue.asJsonPrimitive.isNumber &&
                        oldValue.asJsonPrimitive.isBoolean == templateValue.asJsonPrimitive.isBoolean)) {
                    merged.add(key, oldValue)
                    continue
                }
            }
            merged.add(key, templateValue)
        }
        return merged.toString()
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
