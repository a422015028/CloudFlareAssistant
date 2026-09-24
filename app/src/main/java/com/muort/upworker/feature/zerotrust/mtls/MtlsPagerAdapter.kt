package com.muort.upworker.feature.zerotrust.mtls

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MtlsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MtlsCertificatesFragment()
            1 -> MtlsSettingsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
