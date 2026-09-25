package com.muort.upworker.feature.zerotrust.posture

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DevicePosturePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PostureRulesFragment()
            1 -> PostureIntegrationsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
