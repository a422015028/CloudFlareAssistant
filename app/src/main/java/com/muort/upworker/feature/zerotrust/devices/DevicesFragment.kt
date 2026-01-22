package com.muort.upworker.feature.zerotrust.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Devices Fragment - WARP devices and policies management
 */
@AndroidEntryPoint
class DevicesFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "设备管理功能即将推出\n\n包含：\n• WARP 设备列表\n• 设备撤销管理\n• 设备策略配置"
            textSize = 16f
            setPadding(48, 48, 48, 48)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
    }
}
