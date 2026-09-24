package com.muort.upworker.feature.zerotrust.mtls

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.MtlsCertificate
import com.muort.upworker.databinding.ItemMtlsCertificateBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Adapter for mTLS root certificate list
 */
class MtlsCertificateAdapter(
    private val onCardClick: (MtlsCertificate) -> Unit,
    private val onEditClick: (MtlsCertificate) -> Unit,
    private val onDeleteClick: (MtlsCertificate) -> Unit
) : ListAdapter<MtlsCertificate, MtlsCertificateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMtlsCertificateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onCardClick, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemMtlsCertificateBinding,
        private val onCardClick: (MtlsCertificate) -> Unit,
        private val onEditClick: (MtlsCertificate) -> Unit,
        private val onDeleteClick: (MtlsCertificate) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cert: MtlsCertificate) {
            val ctx = binding.root.context

            binding.certNameText.text = cert.name ?: ctx.getString(R.string.status_unknown)

            val hosts = cert.associatedHostnames.orEmpty()
            binding.certHostnamesText.text = if (hosts.isEmpty()) {
                ctx.getString(R.string.zt_mtls_cert_no_hostnames)
            } else {
                hosts.joinToString(", ")
            }

            val expiresAt = cert.expiresOn?.let {
                runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull()
            }
            if (expiresAt == null) {
                binding.certExpiryText.visibility = View.GONE
            } else {
                binding.certExpiryText.visibility = View.VISIBLE
                binding.certExpiryText.text =
                    ctx.getString(R.string.zt_service_token_expires, DATE_FORMATTER.format(expiresAt))
            }

            val daysLeft = expiresAt?.let { ChronoUnit.DAYS.between(Instant.now(), it.toInstant()) }
            binding.certStatusChip.visibility = View.VISIBLE
            when {
                daysLeft == null -> binding.certStatusChip.visibility = View.GONE
                daysLeft < 0 -> {
                    binding.certStatusChip.text = ctx.getString(R.string.r2_status_expired)
                    binding.certStatusChip.setTextColor(EXPIRED_RED)
                }
                daysLeft <= EXPIRING_SOON_DAYS -> {
                    binding.certStatusChip.text =
                        ctx.getString(R.string.zt_service_token_status_expiring, daysLeft)
                    binding.certStatusChip.setTextColor(EXPIRING_ORANGE)
                }
                else -> {
                    binding.certStatusChip.text = ctx.getString(R.string.zt_service_token_status_active)
                    binding.certStatusChip.setTextColor(ACTIVE_GREEN)
                }
            }

            binding.root.setOnClickListener { onCardClick(cert) }
            binding.editCertificateButton.setOnClickListener { onEditClick(cert) }
            binding.deleteCertificateButton.setOnClickListener { onDeleteClick(cert) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MtlsCertificate>() {
        override fun areItemsTheSame(oldItem: MtlsCertificate, newItem: MtlsCertificate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MtlsCertificate, newItem: MtlsCertificate): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val EXPIRING_SOON_DAYS = 30L
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ACTIVE_GREEN = Color.parseColor("#2E7D32")
        private val EXPIRING_ORANGE = Color.parseColor("#EF6C00")
        private val EXPIRED_RED = Color.parseColor("#C62828")
    }
}
