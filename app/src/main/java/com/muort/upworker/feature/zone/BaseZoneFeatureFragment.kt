package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentZoneFeatureBinding
import com.muort.upworker.databinding.ItemZoneRuleBinding
import com.muort.upworker.feature.account.AccountViewModel
import kotlinx.coroutines.launch

/**
 * 域名级功能页的基类：
 * - 统一读取 zoneId / zoneName / account
 * - 统一 loading / error / empty / list 状态切换
 * - 统一 RecyclerView + 通用 item（标题/副标题/meta/启停开关/删除按钮）
 *
 * 子类只需提供 [zoneFeatureTitle]、[showAddFab] 和绑定逻辑即可。
 */
abstract class BaseZoneFeatureFragment : Fragment() {

    private var _binding: FragmentZoneFeatureBinding? = null
    protected val binding get() = _binding!!

    protected val accountViewModel: AccountViewModel by activityViewModels()

    protected val zoneId: String by lazy { arguments?.getString("zoneId") ?: "" }
    protected val zoneName: String by lazy { arguments?.getString("zoneName") ?: "" }
    protected val account: Account?
        get() = accountViewModel.defaultAccount.value

    /** 空列表时显示的文案。 */
    protected open val emptyText: String = "暂无数据"

    /** 是否显示右下角添加按钮。 */
    protected open val showAddFab: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentZoneFeatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.emptyStateText.text = emptyText
        binding.addFab.visibility = if (showAddFab) View.VISIBLE else View.GONE
        binding.retryButton.setOnClickListener { onRetry() }
        binding.addFab.setOnClickListener { onAddClicked() }
        observeAccount()
    }

    private fun observeAccount() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountViewModel.defaultAccount.collect { acct ->
                    if (acct != null) onAccountReady(acct)
                }
            }
        }
    }

    /** 账号就绪后子类做初始化（绑定 VM、加载列表等）。 */
    protected abstract suspend fun onAccountReady(account: Account)

    /** 重试按钮点击：子类触发重新加载。默认空实现。 */
    protected open fun onRetry() {}

    /** 添加按钮点击：子类弹出添加表单。默认空实现。 */
    protected open fun onAddClicked() {}

    // ==================== UI 状态切换 ====================

    protected fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
    }

    protected fun showList() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
    }

    protected fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.errorStateLayout.visibility = View.GONE
    }

    protected fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.VISIBLE
        binding.errorStateText.text = message
    }

    protected fun toast(msg: String) {
        requireContext().showToast(msg)
    }

    // ==================== 通用列表项适配器 ====================

    /** 通用 zone 规则项数据。 */
    data class ZoneRuleItem(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val meta: String? = null,
        val enabled: Boolean? = null,        // null 表示不显示开关
        val canDelete: Boolean = true,
    )

    /**
     * 通用适配器：每项含标题 / 副标题 / meta / 启停开关 / 删除按钮。
     * 子类通过 [onToggle] / [onDelete] 回调处理操作。
     */
    inner class ZoneRuleAdapter(
        private val onToggle: ((position: Int, item: ZoneRuleItem, enabled: Boolean) -> Unit)? = null,
        private val onDelete: ((position: Int, item: ZoneRuleItem) -> Unit)? = null,
        private val onItemClick: ((position: Int, item: ZoneRuleItem) -> Unit)? = null,
    ) : RecyclerView.Adapter<ZoneRuleAdapter.VH>() {

        private val items = mutableListOf<ZoneRuleItem>()

        fun submitList(newItems: List<ZoneRuleItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun getItem(position: Int): ZoneRuleItem = items[position]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemZoneRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.bind(item, position, onToggle, onDelete, onItemClick)
        }

        override fun getItemCount(): Int = items.size

        inner class VH(private val b: ItemZoneRuleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(
                item: ZoneRuleItem,
                position: Int,
                onToggle: ((Int, ZoneRuleItem, Boolean) -> Unit)?,
                onDelete: ((Int, ZoneRuleItem) -> Unit)?,
                onItemClick: ((Int, ZoneRuleItem) -> Unit)?,
            ) {
                b.titleText.text = item.title
                b.subtitleText.text = item.subtitle ?: ""
                b.subtitleText.visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
                b.metaText.text = item.meta ?: ""
                b.metaText.visibility = if (item.meta.isNullOrEmpty()) View.GONE else View.VISIBLE

                if (item.enabled != null) {
                    b.toggleSwitch.visibility = View.VISIBLE
                    b.toggleSwitch.isChecked = item.enabled
                    b.toggleSwitch.setOnCheckedChangeListener(null)
                    b.toggleSwitch.setOnCheckedChangeListener { _, checked ->
                        onToggle?.invoke(position, item, checked)
                    }
                } else {
                    b.toggleSwitch.visibility = View.GONE
                }

                b.deleteButton.visibility = if (item.canDelete) View.VISIBLE else View.GONE
                b.deleteButton.setOnClickListener {
                    onDelete?.invoke(position, item)
                }

                b.root.setOnClickListener {
                    onItemClick?.invoke(position, item)
                }
            }
        }
    }
}
