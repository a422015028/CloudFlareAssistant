package com.muort.upworker.feature.zerotrust.posture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.muort.upworker.R
import com.muort.upworker.core.model.PostureIntegration
import com.muort.upworker.core.model.PostureIntegrationRequest
import com.muort.upworker.databinding.FragmentPostureIntegrationsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PostureIntegrationsFragment : Fragment() {

    private var _binding: FragmentPostureIntegrationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicePostureViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var integrationAdapter: PostureIntegrationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostureIntegrationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        integrationAdapter = PostureIntegrationAdapter(
            onEditClick = { integration -> showEditDialog(integration) },
            onDeleteClick = { integration -> confirmDelete(integration) }
        )
        binding.integrationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = integrationAdapter
        }
        binding.createIntegrationButton.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrations.collect { integrations ->
                    integrationAdapter.submitList(integrations)
                    binding.integrationsEmptyView.visibility =
                        if (integrations.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showCreateDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_posture_integration_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.integrationNameInput)
        val typeDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.integrationTypeDropdown)
        val intervalDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.integrationIntervalDropdown)
        val configJson = dialogView.findViewById<TextInputEditText>(R.id.integrationConfigJson)

        val labels = PostureTypeSpecs.integrationTypes.map { getString(it.labelRes) }
        typeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        typeDropdown.setText(labels.first(), false)
        typeDropdown.tag = PostureTypeSpecs.integrationTypes.first().type
        configJson.setText(prettyJson(PostureTypeSpecs.integrationTypes.first().configTemplate))
        typeDropdown.setOnItemClickListener { _, _, position, _ ->
            val spec = PostureTypeSpecs.integrationTypes[position]
            typeDropdown.tag = spec.type
            configJson.setText(prettyJson(spec.configTemplate))
        }

        val intervals = PostureTypeSpecs.intervalOptions
        intervalDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, intervals))
        val defaultIntervalIndex = intervals.indexOf("10m")
        intervalDropdown.setText(intervals[defaultIntervalIndex], false)
        val intervalSelection = intArrayOf(defaultIntervalIndex)
        intervalDropdown.setOnItemClickListener { _, _, position, _ ->
            intervalSelection[0] = position
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_integration_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(R.string.zt_posture_integration_name_empty)
                    return@setPositiveButton
                }

                val config = parseJsonObject(configJson.text?.toString()) ?: run {
                    toast(R.string.zt_posture_integration_config_invalid)
                    return@setPositiveButton
                }

                viewModel.createIntegration(
                    account,
                    PostureIntegrationRequest(
                        name = name,
                        type = typeDropdown.tag as String,
                        interval = intervals[intervalSelection[0]],
                        config = config
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(integration: PostureIntegration) {
        val account = accountViewModel.defaultAccount.value ?: return
        val integrationId = integration.id ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_posture_integration_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.integrationNameInput)
        val typeDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.integrationTypeDropdown)
        val intervalDropdown = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.integrationIntervalDropdown)
        val configJson = dialogView.findViewById<TextInputEditText>(R.id.integrationConfigJson)
        val configHint = dialogView.findViewById<android.widget.TextView>(R.id.integrationConfigHint)

        // Pre-fill name
        nameInput.setText(integration.name.orEmpty())

        // Type is locked in edit mode (config shapes are incompatible across types)
        val labels = PostureTypeSpecs.integrationTypes.map { getString(it.labelRes) }
        typeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        val typeIndex = PostureTypeSpecs.integrationTypes.indexOfFirst { it.type == integration.type }
            .coerceAtLeast(0)
        typeDropdown.setText(labels[typeIndex], false)
        typeDropdown.tag = PostureTypeSpecs.integrationTypes[typeIndex].type
        typeDropdown.isEnabled = false

        // Pre-fill interval
        val intervals = PostureTypeSpecs.intervalOptions
        intervalDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, intervals))
        val currentInterval = integration.interval ?: "10m"
        val intervalIndex = intervals.indexOf(currentInterval).coerceAtLeast(intervals.indexOf("10m"))
        intervalDropdown.setText(intervals[intervalIndex], false)
        val intervalSelection = intArrayOf(intervalIndex)
        intervalDropdown.setOnItemClickListener { _, _, position, _ ->
            intervalSelection[0] = position
        }

        // Pre-fill config (secrets are not returned by API; show what we have)
        val existingConfig = integration.config
        val configText = if (existingConfig != null && existingConfig.entrySet().isNotEmpty()) {
            prettyJson(existingConfig.toString())
        } else {
            // Fall back to template for this type
            prettyJson(PostureTypeSpecs.integrationTypes[typeIndex].configTemplate)
        }
        configJson.setText(configText)
        configHint.hint = getString(R.string.zt_posture_integration_config_hint_edit)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_integration_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    toast(R.string.zt_posture_integration_name_empty)
                    return@setPositiveButton
                }

                val config = parseJsonObject(configJson.text?.toString()) ?: run {
                    toast(R.string.zt_posture_integration_config_invalid)
                    return@setPositiveButton
                }

                viewModel.updateIntegration(
                    account,
                    integrationId,
                    PostureIntegrationRequest(
                        name = name,
                        type = typeDropdown.tag as String,
                        interval = intervals[intervalSelection[0]],
                        config = config
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(integration: PostureIntegration) {
        val account = accountViewModel.defaultAccount.value ?: return
        val integrationId = integration.id ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_posture_integration_delete_title)
            .setMessage(getString(R.string.zt_posture_integration_delete_confirm, integration.name ?: integrationId))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteIntegration(account, integrationId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseJsonObject(raw: String?): JsonObject? {
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

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
