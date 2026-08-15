package com.asdk.tools.wpstatussaver.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.asdk.tools.wpstatussaver.ui.SavedListFragment
import com.asdk.tools.wpstatussaver.ui.StatusListFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        const val TAB_PHOTOS = 0
        const val TAB_VIDEOS = 1
        const val TAB_SAVED = 2
        const val TOTAL_TABS = 3
    }

    override fun getItemCount(): Int = TOTAL_TABS

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_PHOTOS -> StatusListFragment.newInstance(isVideoOnly = false)
            TAB_VIDEOS -> StatusListFragment.newInstance(isVideoOnly = true)
            TAB_SAVED -> SavedListFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
