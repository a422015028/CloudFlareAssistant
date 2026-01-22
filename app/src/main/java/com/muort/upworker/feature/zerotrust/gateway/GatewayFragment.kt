package com.muort.upworker.feature.zerotrust.gateway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Gateway Fragment - Rules, Lists, and Locations management
 */
@AndroidEntryPoint
class GatewayFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "Gateway 功能即将推出\n\n包含：\n• DNS/HTTP/L4 规则管理\n• 自定义列表管理\n• 网络位置配置"
            textSize = 16f
            setPadding(48, 48, 48, 48)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
    }
}
