package com.muort.upworker.feature.zerotrust.servicetoken

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.ServiceToken
import com.muort.upworker.databinding.ItemServiceTokenBinding
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Adapter for Access Service Token list
 */
class ServiceTokenAdapter(
    private val onCardClick: (ServiceToken) -> Unit,
    private val onCopyIdClick: (ServiceToken) -> Unit,
    private val onMoreClick: (View, ServiceToken) -> Unit
) : ListAdapter<ServiceToken, ServiceTokenAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServiceTokenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onCardClick, onCopyIdClick, onMoreClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemServiceTokenBinding,
        private val onCardClick: (ServiceToken) -> Unit,
        private val onCopyIdClick: (ServiceToken) -> Unit,
        private val onMoreClick: (View, ServiceToken) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(token: ServiceToken) {
            val ctx = binding.root.context

            val disabled = token.enabled == false
            binding.tokenNameText.text = token.name
            binding.tokenNameText.alpha = if (disabled) 0.45f else 1f
            binding.tokenClientIdText.text = token.clientId
                ?: ctx.getString(R.string.status_unknown)
            binding.tokenClientIdText.alpha = if (disabled) 0.45f else 1f

            bindExpiry(token, disabled)
            bindLastSeen(token, disabled)

            binding.root.setOnClickListener { onCardClick(token) }
            binding.copyIdButton.setOnClickListener { onCopyIdClick(token) }
            binding.moreButton.setOnClickListener { onMoreClick(it, token) }
        }

        private fun bindExpiry(token: ServiceToken, disabled: Boolean) {
            val ctx = binding.root.context
            val expiresAt = parseServiceTokenInstant(token.expiresAt)

            if (expiresAt == null) {
                binding.tokenExpiryText.text = if (token.duration == DURATION_FOREVER) {
                    ctx.getString(R.string.status_never_expires)
                } else {
                    null
                }
                binding.tokenExpiryText.visibility = if (binding.tokenExpiryText.text.isNullOrEmpty()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            } else {
                binding.tokenExpiryText.visibility = View.VISIBLE
                binding.tokenExpiryText.text = ctx.getString(
                    R.string.zt_service_token_expires,
                    DATE_FORMATTER.format(expiresAt)
                )
            }

            binding.tokenStatusChip.visibility = View.VISIBLE
            when {
                disabled -> {
                    binding.tokenStatusChip.text = ctx.getString(R.string.msg_disabled)
                    binding.tokenStatusChip.setTextColor(STATUS_GREY)
                }
                expiresAt != null && ChronoUnit.DAYS.between(Instant.now(), expiresAt.toInstant()) < 0 -> {
                    binding.tokenStatusChip.text = ctx.getString(R.string.r2_status_expired)
                    binding.tokenStatusChip.setTextColor(EXPIRED_RED)
                }
                expiresAt != null &&
                    ChronoUnit.DAYS.between(Instant.now(), expiresAt.toInstant()) <= EXPIRING_SOON_DAYS -> {
                    val daysLeft = ChronoUnit.DAYS.between(Instant.now(), expiresAt.toInstant())
                    binding.tokenStatusChip.text =
                        ctx.getString(R.string.zt_service_token_status_expiring, daysLeft)
                    binding.tokenStatusChip.setTextColor(EXPIRING_ORANGE)
                }
                else -> {
                    binding.tokenStatusChip.text = ctx.getString(R.string.zt_service_token_status_active)
                    binding.tokenStatusChip.setTextColor(ACTIVE_GREEN)
                }
            }
        }

        private fun bindLastSeen(token: ServiceToken, disabled: Boolean) {
            val ctx = binding.root.context
            val lastSeen = formatServiceTokenDateTime(token.lastSeenAt)
            if (lastSeen == null) {
                binding.tokenLastSeenText.visibility = View.GONE
            } else {
                binding.tokenLastSeenText.visibility = View.VISIBLE
                binding.tokenLastSeenText.text = ctx.getString(R.string.token_detail_last_used, lastSeen)
                binding.tokenLastSeenText.alpha = if (disabled) 0.45f else 1f
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ServiceToken>() {
        override fun areItemsTheSame(oldItem: ServiceToken, newItem: ServiceToken): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ServiceToken, newItem: ServiceToken): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        const val DURATION_FOREVER = "forever"
        private const val EXPIRING_SOON_DAYS = 30L
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val ACTIVE_GREEN = Color.parseColor("#2E7D32")
        private val EXPIRING_ORANGE = Color.parseColor("#EF6C00")
        private val EXPIRED_RED = Color.parseColor("#C62828")
        private val STATUS_GREY = Color.parseColor("#757575")
    }
}

/** Parse an RFC3339 timestamp returned by the API to device-local time. */
internal fun parseServiceTokenInstant(raw: String?): ZonedDateTime? =
    raw?.let {
        runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull()
    }

/** Format an RFC3339 timestamp as device-local "yyyy-MM-dd HH:mm", or null when unparseable/empty. */
internal fun formatServiceTokenDateTime(raw: String?): String? =
    parseServiceTokenInstant(raw)?.let { ServiceTokenAdapter.DATE_TIME_FORMATTER.format(it) }
