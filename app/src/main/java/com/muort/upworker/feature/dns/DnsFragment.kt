package com.muort.upworker.feature.dns

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.R
import com.muort.upworker.core.model.DnsRecord
import com.muort.upworker.databinding.DialogDnsRecordInputBinding
import com.muort.upworker.databinding.FragmentDnsBinding
import com.muort.upworker.databinding.ItemDnsRecordBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DnsFragment : Fragment() {
    
    private var _binding: FragmentDnsBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    
    private lateinit var dnsAdapter: DnsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupClickListeners()
        observeViewModel()

        // 优先从导航参数获取 zoneId，回退到账号默认 zoneId
        val zoneId = arguments?.getString("zoneId") ?: accountViewModel.defaultAccount.value?.zoneId
        if (!zoneId.isNullOrBlank()) {
            dnsViewModel.setZoneId(zoneId)
            timber.log.Timber.d("DNS Fragment: Loading records for zoneId: $zoneId")
            accountViewModel.defaultAccount.value?.let { dnsViewModel.loadDnsRecords(it) }
        } else {
            timber.log.Timber.w("DNS Fragment: No zoneId available")
            Snackbar.make(binding.root, "请先选择或添加域名", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun setupAdapter() {
        dnsAdapter = DnsAdapter(
            onEditClick = { record ->
                showEditRecordDialog(record)
            },
            onDeleteClick = { record ->
                showDeleteRecordDialog(record)
            }
        )
        binding.dnsRecyclerView.adapter = dnsAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddRecord.setOnClickListener {
            showAddRecordDialog()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dnsViewModel.dnsRecords.collect { records ->
                        dnsAdapter.submitList(records)
                        binding.emptyText.visibility = 
                            if (records.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    dnsViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    dnsViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            dnsViewModel.loadDnsRecords(account)
                        }
                    }
                }
            }
        }
    }
    
    private fun showAddRecordDialog() {
        showRecordDialog(null)
    }

    private fun showEditRecordDialog(record: DnsRecord) {
        showRecordDialog(record)
    }

    private fun showRecordDialog(existingRecord: DnsRecord?) {
        val dialogBinding = DialogDnsRecordInputBinding.inflate(layoutInflater)

        val types = resources.getStringArray(R.array.dns_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        dialogBinding.dnsType.setAdapter(adapter)

        // 字段输入引用：config -> EditText
        val fieldInputs = mutableListOf<Pair<DnsFieldConfig, TextInputEditText>>()

        // 根据类型动态生成输入字段
        fun generateFields(type: String) {
            val container = dialogBinding.dnsFieldsContainer
            container.removeAllViews()
            fieldInputs.clear()

            val configs = dnsFieldConfigs[type] ?: return
            val density = resources.displayMetrics.density
            val margin = (8 * density).toInt()

            for (config in configs) {
                val textInputLayout = TextInputLayout(requireContext()).apply {
                    hint = config.label
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = margin
                textInputLayout.layoutParams = params

                val editText = TextInputEditText(textInputLayout.context)
                editText.inputType = if (config.isNumber) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                editText.maxLines = 1
                textInputLayout.addView(editText)

                // 编辑模式填充现有值
                if (existingRecord != null) {
                    editText.setText(getFieldValue(existingRecord, config))
                }

                container.addView(textInputLayout)
                fieldInputs.add(config to editText)
            }
        }

        // 类型选择变化时重新生成字段
        dialogBinding.dnsType.setOnItemClickListener { _, _, position, _ ->
            val type = types[position]
            generateFields(type)
            // 代理开关仅对 A/AAAA/CNAME 可见
            val supportProxied = type == "A" || type == "AAAA" || type == "CNAME"
            dialogBinding.dnsProxied.visibility = if (supportProxied) View.VISIBLE else View.GONE
            if (!supportProxied) dialogBinding.dnsProxied.isChecked = false
        }

        // 初始化类型和字段
        if (existingRecord != null) {
            dialogBinding.dnsType.setText(existingRecord.type, false)
            dialogBinding.dnsName.setText(existingRecord.name)
            dialogBinding.dnsTtl.setText(existingRecord.ttl.toString())
            dialogBinding.dnsProxied.isChecked = existingRecord.proxied
            generateFields(existingRecord.type)
            val supportProxied = existingRecord.type == "A" || existingRecord.type == "AAAA" || existingRecord.type == "CNAME"
            dialogBinding.dnsProxied.visibility = if (supportProxied) View.VISIBLE else View.GONE
        } else {
            dialogBinding.dnsType.setText(types[0], false)
            dialogBinding.dnsProxied.isChecked = false
            generateFields(types[0])
            // types[0] 是 "A"，支持代理
            dialogBinding.dnsProxied.visibility = View.VISIBLE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val type = dialogBinding.dnsType.text.toString()
                val name = dialogBinding.dnsName.text.toString()
                val ttl = dialogBinding.dnsTtl.text.toString().toIntOrNull() ?: 1
                val proxied = dialogBinding.dnsProxied.isChecked

                // 收集字段值
                var content: String? = null
                var priority: Int? = null
                val dataMap = mutableMapOf<String, Any?>()

                for ((config, editText) in fieldInputs) {
                    val value = editText.text.toString()
                    when (config.location) {
                        FieldLocation.CONTENT -> content = value
                        FieldLocation.TOP_LEVEL -> {
                            if (config.key == "priority") priority = value.toIntOrNull()
                        }
                        FieldLocation.DATA -> {
                            dataMap[config.key] = if (config.isNumber) value.toIntOrNull() else value
                        }
                    }
                }

                val data = if (dataMap.isEmpty()) null else dataMap

                accountViewModel.defaultAccount.value?.let { account ->
                    if (existingRecord != null) {
                        dnsViewModel.updateDnsRecord(
                            account, existingRecord.id, type, name, content, ttl, proxied, priority, data
                        )
                    } else {
                        dnsViewModel.createDnsRecord(
                            account, type, name, content, ttl, proxied, priority, data
                        )
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getFieldValue(record: DnsRecord, config: DnsFieldConfig): String {
        return when (config.location) {
            FieldLocation.CONTENT -> record.content ?: ""
            FieldLocation.TOP_LEVEL -> {
                if (config.key == "priority") record.priority?.toString() ?: "" else ""
            }
            FieldLocation.DATA -> record.data?.get(config.key)?.toString() ?: ""
        }
    }
    
    private fun showDeleteRecordDialog(record: DnsRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除 DNS 记录")
            .setMessage("确定要删除记录 \"${record.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    dnsViewModel.deleteDnsRecord(account, record.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        enum class FieldLocation { CONTENT, TOP_LEVEL, DATA }

        data class DnsFieldConfig(
            val key: String,
            val label: String,
            val isNumber: Boolean,
            val location: FieldLocation
        )

        private val dnsFieldConfigs: Map<String, List<DnsFieldConfig>> = mapOf(
            "A" to listOf(DnsFieldConfig("content", "IPv4 地址", false, FieldLocation.CONTENT)),
            "AAAA" to listOf(DnsFieldConfig("content", "IPv6 地址", false, FieldLocation.CONTENT)),
            "CNAME" to listOf(DnsFieldConfig("content", "目标域名", false, FieldLocation.CONTENT)),
            "TXT" to listOf(DnsFieldConfig("content", "文本内容", false, FieldLocation.CONTENT)),
            "NS" to listOf(DnsFieldConfig("content", "NS 服务器", false, FieldLocation.CONTENT)),
            "PTR" to listOf(DnsFieldConfig("content", "目标域名", false, FieldLocation.CONTENT)),
            "MX" to listOf(
                DnsFieldConfig("priority", "优先级", true, FieldLocation.TOP_LEVEL),
                DnsFieldConfig("content", "邮件服务器", false, FieldLocation.CONTENT)
            ),
            "SRV" to listOf(
                DnsFieldConfig("priority", "优先级", true, FieldLocation.DATA),
                DnsFieldConfig("weight", "权重", true, FieldLocation.DATA),
                DnsFieldConfig("port", "端口", true, FieldLocation.DATA),
                DnsFieldConfig("target", "目标主机", false, FieldLocation.DATA)
            ),
            "LOC" to listOf(
                DnsFieldConfig("lat_degrees", "纬度(度)", true, FieldLocation.DATA),
                DnsFieldConfig("lat_minutes", "纬度(分)", true, FieldLocation.DATA),
                DnsFieldConfig("lat_seconds", "纬度(秒)", true, FieldLocation.DATA),
                DnsFieldConfig("lat_direction", "纬度方向", false, FieldLocation.DATA),
                DnsFieldConfig("long_degrees", "经度(度)", true, FieldLocation.DATA),
                DnsFieldConfig("long_minutes", "经度(分)", true, FieldLocation.DATA),
                DnsFieldConfig("long_seconds", "经度(秒)", true, FieldLocation.DATA),
                DnsFieldConfig("long_direction", "经度方向", false, FieldLocation.DATA),
                DnsFieldConfig("altitude", "海拔(m)", true, FieldLocation.DATA),
                DnsFieldConfig("size", "大小(m)", true, FieldLocation.DATA),
                DnsFieldConfig("precision_horz", "水平精度(m)", true, FieldLocation.DATA),
                DnsFieldConfig("precision_vert", "垂直精度(m)", true, FieldLocation.DATA)
            ),
            "CERT" to listOf(
                DnsFieldConfig("type", "类型", true, FieldLocation.DATA),
                DnsFieldConfig("key_tag", "Key Tag", true, FieldLocation.DATA),
                DnsFieldConfig("algorithm", "算法", true, FieldLocation.DATA),
                DnsFieldConfig("certificate", "证书(Base64)", false, FieldLocation.DATA)
            ),
            "DS" to listOf(
                DnsFieldConfig("key_tag", "Key Tag", true, FieldLocation.DATA),
                DnsFieldConfig("algorithm", "算法", true, FieldLocation.DATA),
                DnsFieldConfig("digest_type", "摘要类型", true, FieldLocation.DATA),
                DnsFieldConfig("digest", "摘要", false, FieldLocation.DATA)
            ),
            "NAPTR" to listOf(
                DnsFieldConfig("order", "顺序", true, FieldLocation.DATA),
                DnsFieldConfig("preference", "偏好", true, FieldLocation.DATA),
                DnsFieldConfig("flags", "标志", false, FieldLocation.DATA),
                DnsFieldConfig("service", "服务", false, FieldLocation.DATA),
                DnsFieldConfig("regex", "正则", false, FieldLocation.DATA),
                DnsFieldConfig("replacement", "替换", false, FieldLocation.DATA)
            ),
            "SMIMEA" to listOf(
                DnsFieldConfig("usage", "用法", true, FieldLocation.DATA),
                DnsFieldConfig("selector", "选择器", true, FieldLocation.DATA),
                DnsFieldConfig("matching_type", "匹配类型", true, FieldLocation.DATA),
                DnsFieldConfig("certificate", "证书", false, FieldLocation.DATA)
            ),
            "SSHFP" to listOf(
                DnsFieldConfig("algorithm", "算法", true, FieldLocation.DATA),
                DnsFieldConfig("type", "类型", true, FieldLocation.DATA),
                DnsFieldConfig("fingerprint", "指纹", false, FieldLocation.DATA)
            ),
            "SVCB" to listOf(
                DnsFieldConfig("priority", "优先级", true, FieldLocation.DATA),
                DnsFieldConfig("target", "目标", false, FieldLocation.DATA),
                DnsFieldConfig("value", "值", false, FieldLocation.DATA)
            ),
            "TLSA" to listOf(
                DnsFieldConfig("usage", "用法", true, FieldLocation.DATA),
                DnsFieldConfig("selector", "选择器", true, FieldLocation.DATA),
                DnsFieldConfig("matching_type", "匹配类型", true, FieldLocation.DATA),
                DnsFieldConfig("certificate", "证书", false, FieldLocation.DATA)
            ),
            "URI" to listOf(
                DnsFieldConfig("priority", "优先级", true, FieldLocation.TOP_LEVEL),
                DnsFieldConfig("weight", "权重", true, FieldLocation.DATA),
                DnsFieldConfig("target", "目标 URI", false, FieldLocation.DATA)
            ),
            "HTTPS" to listOf(
                DnsFieldConfig("priority", "优先级", true, FieldLocation.DATA),
                DnsFieldConfig("target", "目标", false, FieldLocation.DATA),
                DnsFieldConfig("value", "值", false, FieldLocation.DATA)
            )
        )
    }

    private class DnsAdapter(
        private val onEditClick: (DnsRecord) -> Unit,
        private val onDeleteClick: (DnsRecord) -> Unit
    ) : RecyclerView.Adapter<DnsAdapter.ViewHolder>() {
        
        private var records = listOf<DnsRecord>()
        
        fun submitList(newList: List<DnsRecord>) {
            records = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDnsRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(records[position])
        }
        
        override fun getItemCount() = records.size
        
        inner class ViewHolder(
            private val binding: ItemDnsRecordBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(record: DnsRecord) {
                binding.dnsNameText.text = record.name
                binding.dnsTypeText.text = record.type
                // content 可能为 null（使用 data 字段的记录），回退到 data 值拼接
                val displayContent = record.content
                    ?: record.data?.values?.filterNotNull()?.joinToString(" ")
                    ?: ""
                binding.dnsContentText.text = "→ $displayContent"
                binding.dnsTtlText.text = "TTL: ${record.ttl}"
                binding.dnsProxiedText.text = if (record.proxied) "🟠 已代理" else "⚪ 仅 DNS"
                
                // 点击列表项直接显示编辑窗口
                binding.root.setOnClickListener {
                    onEditClick(record)
                }
                
                binding.dnsMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit -> {
                                    onEditClick(record)
                                    true
                                }
                                R.id.action_delete -> {
                                    onDeleteClick(record)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
}
