package com.muort.upworker.feature.zerotrust.devices

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DevicesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DevicesListFragment()
            1 -> DevicePoliciesFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
