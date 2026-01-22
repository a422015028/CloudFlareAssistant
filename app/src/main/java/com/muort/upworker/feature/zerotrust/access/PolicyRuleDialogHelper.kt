package com.muort.upworker.feature.zerotrust.access

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessRule

/**
 * Helper class for showing rule add/edit dialogs
 */
class PolicyRuleDialogHelper(private val context: Context) {

    private val ruleTypes = listOf(
        "email" to "Email 地址",
        "email_domain" to "Email 域名",
        "ip" to "IP 地址",
        "access_group" to "Access 组",
        "geo" to "地理位置",
        "everyone" to "所有人",
        "common_name" to "证书通用名称"
    )

    /**
     * Show dialog to add a new rule
     * @param groups List of available Access Groups for group selector
     * @param onRuleAdded Callback with the created rule
     */
    fun showAddRuleDialog(
        groups: List<com.muort.upworker.core.model.AccessGroup> = emptyList(),
        onRuleAdded: (AccessRule) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_policy_rule, null)
        
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.ruleTypeSpinner)
        val emailLayout = dialogView.findViewById<TextInputLayout>(R.id.emailLayout)
        val emailInput = dialogView.findViewById<TextInputEditText>(R.id.emailInput)
        val emailDomainLayout = dialogView.findViewById<TextInputLayout>(R.id.emailDomainLayout)
        val emailDomainInput = dialogView.findViewById<TextInputEditText>(R.id.emailDomainInput)
        val ipLayout = dialogView.findViewById<TextInputLayout>(R.id.ipLayout)
        val ipInput = dialogView.findViewById<TextInputEditText>(R.id.ipInput)
        val groupLayout = dialogView.findViewById<View>(R.id.groupLayout)
        val groupSpinner = dialogView.findViewById<Spinner>(R.id.groupSpinner)
        val geoLayout = dialogView.findViewById<TextInputLayout>(R.id.geoLayout)
        val geoInput = dialogView.findViewById<TextInputEditText>(R.id.geoInput)
        val everyoneText = dialogView.findViewById<View>(R.id.everyoneText)
        val commonNameLayout = dialogView.findViewById<TextInputLayout>(R.id.commonNameLayout)
        val commonNameInput = dialogView.findViewById<TextInputEditText>(R.id.commonNameInput)

        // Setup type spinner
        val typeAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            ruleTypes.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        // Setup group spinner if groups are available
        if (groups.isNotEmpty()) {
            val groupAdapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                groups.map { it.name }
            )
            groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            groupSpinner.adapter = groupAdapter
        }

        // Show/hide input fields based on selected type
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = ruleTypes[position].first
                
                // Hide all inputs first
                emailLayout.visibility = View.GONE
                emailDomainLayout.visibility = View.GONE
                ipLayout.visibility = View.GONE
                groupLayout.visibility = View.GONE
                geoLayout.visibility = View.GONE
                everyoneText.visibility = View.GONE
                commonNameLayout.visibility = View.GONE

                // Show relevant input
                when (selectedType) {
                    "email" -> emailLayout.visibility = View.VISIBLE
                    "email_domain" -> emailDomainLayout.visibility = View.VISIBLE
                    "ip" -> ipLayout.visibility = View.VISIBLE
                    "access_group" -> groupLayout.visibility = View.VISIBLE
                    "geo" -> geoLayout.visibility = View.VISIBLE
                    "everyone" -> everyoneText.visibility = View.VISIBLE
                    "common_name" -> commonNameLayout.visibility = View.VISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("添加规则")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val selectedType = ruleTypes[typeSpinner.selectedItemPosition].first
                val rule = createRule(
                    selectedType,
                    emailInput.text.toString(),
                    emailDomainInput.text.toString(),
                    ipInput.text.toString(),
                    if (groups.isNotEmpty()) groups[groupSpinner.selectedItemPosition].id else "",
                    geoInput.text.toString(),
                    commonNameInput.text.toString()
                )
                
                rule?.let { onRuleAdded(it) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createRule(
        type: String,
        email: String,
        emailDomain: String,
        ip: String,
        groupId: String,
        geo: String,
        commonName: String
    ): AccessRule? {
        return when (type) {
            "email" -> {
                if (email.isBlank()) return null
                AccessRule(email = mapOf("email" to email))
            }
            "email_domain" -> {
                if (emailDomain.isBlank()) return null
                AccessRule(emailDomain = mapOf("domain" to emailDomain))
            }
            "ip" -> {
                if (ip.isBlank()) return null
                AccessRule(ip = mapOf("ip" to ip))
            }
            "access_group" -> {
                if (groupId.isBlank()) return null
                AccessRule(accessGroup = mapOf("id" to groupId))
            }
            "geo" -> {
                if (geo.isBlank()) return null
                // Split by comma and trim
                val countryCodes = geo.split(",").map { it.trim().uppercase() }
                AccessRule(geo = mapOf("country_code" to countryCodes))
            }
            "everyone" -> {
                AccessRule(everyone = emptyMap())
            }
            "common_name" -> {
                if (commonName.isBlank()) return null
                AccessRule(commonName = mapOf("common_name" to commonName))
            }
            else -> null
        }
    }
}
