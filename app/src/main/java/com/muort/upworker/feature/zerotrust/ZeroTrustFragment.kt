package com.muort.upworker.feature.zerotrust

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.muort.upworker.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Zero Trust (Cloudflare One) entry fragment
 * Provides navigation to Access, Gateway, Devices, and Tunnels
 */
@AndroidEntryPoint
class ZeroTrustFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_zero_trust, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<MaterialCardView>(R.id.accessCard).setOnClickListener {
            findNavController().navigate(R.id.action_zeroTrust_to_access)
        }
        
        view.findViewById<MaterialCardView>(R.id.gatewayCard).setOnClickListener {
            findNavController().navigate(R.id.action_zeroTrust_to_gateway)
        }
        
        view.findViewById<MaterialCardView>(R.id.devicesCard).setOnClickListener {
            findNavController().navigate(R.id.action_zeroTrust_to_devices)
        }
        
        view.findViewById<MaterialCardView>(R.id.tunnelsCard).setOnClickListener {
            findNavController().navigate(R.id.action_zeroTrust_to_tunnels)
        }
    }
}
