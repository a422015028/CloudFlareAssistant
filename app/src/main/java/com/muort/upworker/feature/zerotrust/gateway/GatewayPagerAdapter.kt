package com.muort.upworker.feature.zerotrust.gateway

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class GatewayPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GatewayRulesFragment()
            1 -> GatewayListsFragment()
            2 -> GatewayLocationsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
