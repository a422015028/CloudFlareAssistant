package com.muort.upworker.feature.zerotrust.servicecredentials

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
 * Landing page for service-level credentials:
 * Service Tokens, Mutual TLS certificates, and (planned) SSH short-lived certificates.
 */
@AndroidEntryPoint
class ServiceCredentialsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_service_credentials, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialCardView>(R.id.serviceTokensEntryCard).setOnClickListener {
            findNavController().navigate(R.id.action_serviceCredentials_to_serviceTokens)
        }

        view.findViewById<MaterialCardView>(R.id.mtlsEntryCard).setOnClickListener {
            findNavController().navigate(R.id.action_serviceCredentials_to_mtls)
        }

        view.findViewById<MaterialCardView>(R.id.sshEntryCard).setOnClickListener {
            findNavController().navigate(R.id.action_serviceCredentials_to_sshCa)
        }
    }
}
