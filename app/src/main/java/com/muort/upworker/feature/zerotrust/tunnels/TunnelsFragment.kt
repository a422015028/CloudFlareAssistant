package com.muort.upworker.feature.zerotrust.tunnels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tunnels Fragment - Cloudflare Tunnel management
 */
@AndroidEntryPoint
class TunnelsFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "Cloudflare Tunnel 功能即将推出\n\n包含：\n• 隧道列表查看\n• 隧道创建和删除\n• 连接状态监控\n• 配置管理"
            textSize = 16f
            setPadding(48, 48, 48, 48)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
    }
}
