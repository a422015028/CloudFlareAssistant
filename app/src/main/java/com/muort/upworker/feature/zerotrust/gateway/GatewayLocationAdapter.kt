package com.muort.upworker.feature.zerotrust.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.GatewayLocation
import com.muort.upworker.databinding.ItemGatewayLocationBinding

class GatewayLocationAdapter(
    private val onEditClick: (GatewayLocation) -> Unit,
    private val onDeleteClick: (GatewayLocation) -> Unit
) : ListAdapter<GatewayLocation, GatewayLocationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGatewayLocationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemGatewayLocationBinding,
        private val onEditClick: (GatewayLocation) -> Unit,
        private val onDeleteClick: (GatewayLocation) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: GatewayLocation) {
            binding.locationNameText.text = location.name

            binding.defaultChip.visibility = if (location.clientDefault == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            binding.ecsChip.visibility = if (location.ecsSupport == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            val networks = location.networks?.joinToString(", ") { it.network } ?: "无"
            binding.locationNetworksText.text = "来源 IP 白名单: $networks"

            binding.locationClientsText.text = "客户端: ${location.clientCount ?: 0}"

            val endpoints = location.endpoints
            val ipv4 = location.ipv4Destination
            val ipv4Backup = location.ipv4DestinationBackup
            val ipv6 = location.ip
            val gatewayHost = location.dohSubdomain?.let { "$it.cloudflare-gateway.com" }

            val ipv4Display = buildString {
                append("IPv4: ").append(ipv4 ?: "未分配")
                if (!ipv4Backup.isNullOrBlank()) append("（备用 $ipv4Backup）")
            }
            val ipv4Copy = if (ipv4.isNullOrBlank()) null else listOfNotNull(ipv4, ipv4Backup).joinToString(", ")

            setEndpointText(binding.dnsIpv4Text, ipv4Display, endpoints?.ipv4?.enabled)
            setEndpointText(binding.dnsIpv6Text, "IPv6: ${ipv6 ?: "未分配"}", endpoints?.ipv6?.enabled)
            setEndpointText(
                binding.dnsDotText,
                gatewayHost?.let { "DoT: $it:853" } ?: "DoT: 未分配",
                endpoints?.dot?.enabled
            )
            val dohUrl = gatewayHost?.let { "https://$it/dns-query" }
            setEndpointText(binding.dnsDohText, "DoH: ${dohUrl ?: "未分配"}", endpoints?.doh?.enabled)

            binding.dnsIpv4Text.setOnClickListener {
                if (!ipv4Copy.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, ipv4Copy, "IPv4 地址")
                }
            }

            binding.dnsIpv6Text.setOnClickListener {
                if (!ipv6.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, ipv6, "IPv6 地址")
                }
            }

            binding.dnsDotText.setOnClickListener {
                gatewayHost?.let { copyToClipboard(binding.root.context, "$it:853", "DoT 地址") }
            }

            binding.dnsDohText.setOnClickListener {
                if (!dohUrl.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, dohUrl, "DoH 地址")
                }
            }

            binding.editButton.setOnClickListener { onEditClick(location) }

            binding.deleteButton.setOnClickListener {
                if (location.clientDefault == true) {
                    Toast.makeText(binding.root.context, "无法删除默认位置，请先设置其他位置为默认", Toast.LENGTH_SHORT).show()
                } else {
                    onDeleteClick(location)
                }
            }
        }

        private fun setEndpointText(textView: android.widget.TextView, text: String, enabled: Boolean?) {
            if (enabled == false) {
                val spannable = SpannableString("$text（已停用）")
                val typedValue = android.util.TypedValue()
                textView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorError, typedValue, true)
                spannable.setSpan(
                    ForegroundColorSpan(typedValue.data),
                    text.length,
                    spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannable
            } else {
                textView.text = text
            }
        }
        
        private fun copyToClipboard(context: Context, text: String, label: String) {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(context, "$label 已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GatewayLocation>() {
        override fun areItemsTheSame(oldItem: GatewayLocation, newItem: GatewayLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GatewayLocation, newItem: GatewayLocation): Boolean {
            return oldItem == newItem
        }
    }
}