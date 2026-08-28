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
import com.muort.upworker.R
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
            val ctx = binding.root.context
            binding.locationNameText.text = location.name

            binding.defaultChip.visibility = if (location.clientDefault == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            binding.defaultChip.text = ctx.getString(R.string.zt_location_default_label)

            binding.ecsChip.visibility = if (location.ecsSupport == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            binding.ecsChip.text = ctx.getString(R.string.zt_location_ecs_label)

            val networks = location.networks?.joinToString(", ") { it.network } ?: ctx.getString(R.string.zt_location_no_networks)
            binding.locationNetworksText.text = ctx.getString(R.string.zt_location_networks_label, networks)

            val clientCount = location.clientCount ?: 0
            binding.locationClientsText.text = ctx.resources.getQuantityString(R.plurals.zt_location_clients, clientCount, clientCount)

            val endpoints = location.endpoints
            val ipv4 = location.ipv4Destination
            val ipv4Backup = location.ipv4DestinationBackup
            val ipv6 = location.ip
            val gatewayHost = location.dohSubdomain?.let { "$it.cloudflare-gateway.com" }

            val ipv4Main = ipv4 ?: ctx.getString(R.string.zt_location_ipv4_not_assigned)
            val ipv4BackupPart = if (!ipv4Backup.isNullOrBlank()) ctx.getString(R.string.zt_location_ipv4_backup, ipv4Backup) else ""
            val ipv4Display = ctx.getString(R.string.zt_location_ipv4_label, ipv4Main) + ipv4BackupPart
            val ipv4Copy = if (ipv4.isNullOrBlank()) null else listOfNotNull(ipv4, ipv4Backup).joinToString(", ")

            setEndpointText(ctx, binding.dnsIpv4Text, ipv4Display, endpoints?.ipv4?.enabled)
            setEndpointText(ctx, binding.dnsIpv6Text, ctx.getString(R.string.zt_location_ipv6_label, ipv6 ?: ctx.getString(R.string.zt_location_ipv4_not_assigned)), endpoints?.ipv6?.enabled)
            setEndpointText(
                ctx,
                binding.dnsDotText,
                if (gatewayHost != null) ctx.getString(R.string.zt_location_dot_label, gatewayHost) else ctx.getString(R.string.zt_location_dot_unassigned),
                endpoints?.dot?.enabled
            )
            val dohUrl = gatewayHost?.let { "https://$it/dns-query" }
            setEndpointText(ctx, binding.dnsDohText, ctx.getString(R.string.zt_location_doh_label, dohUrl ?: ctx.getString(R.string.zt_location_ipv4_not_assigned)), endpoints?.doh?.enabled)

            binding.dnsIpv4Text.setOnClickListener {
                if (!ipv4Copy.isNullOrBlank()) {
                    copyToClipboard(ctx, ipv4Copy, "IPv4")
                }
            }

            binding.dnsIpv6Text.setOnClickListener {
                if (!ipv6.isNullOrBlank()) {
                    copyToClipboard(ctx, ipv6, "IPv6")
                }
            }

            binding.dnsDotText.setOnClickListener {
                gatewayHost?.let { copyToClipboard(ctx, "$it:853", "DoT") }
            }

            binding.dnsDohText.setOnClickListener {
                if (!dohUrl.isNullOrBlank()) {
                    copyToClipboard(ctx, dohUrl, "DoH")
                }
            }

            binding.editButton.setOnClickListener { onEditClick(location) }

            binding.deleteButton.setOnClickListener {
                if (location.clientDefault == true) {
                    Toast.makeText(ctx, ctx.getString(R.string.zt_location_cannot_delete_default), Toast.LENGTH_SHORT).show()
                } else {
                    onDeleteClick(location)
                }
            }
        }

        private fun setEndpointText(ctx: Context, textView: android.widget.TextView, text: String, enabled: Boolean?) {
            if (enabled == false) {
                val spannable = SpannableString(text + ctx.getString(R.string.zt_location_endpoint_disabled))
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
            Toast.makeText(context, context.getString(R.string.zt_location_copy_label_format, label), Toast.LENGTH_SHORT).show()
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