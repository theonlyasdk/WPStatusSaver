package com.asdk.tools.wpstatussaver.util

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class WhatsAppPageTransformer(private val pageMarginPx: Int = 48) : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        page.translationX = -position * pageMarginPx

        when {
            position < -1 -> {
                // Page is way off-screen to the left
                page.alpha = 0f
            }
            position <= 0 -> {
                // Page is moving to the left [-1, 0]
                page.alpha = 1f
                page.scaleX = 1f
                page.scaleY = 1f
            }
            position <= 1 -> {
                // Page is moving to the right [0, 1]
                page.alpha = 1f - 0.25f * abs(position)
                val scale = 0.94f + (1f - 0.94f) * (1f - abs(position))
                page.scaleX = scale
                page.scaleY = scale
            }
            else -> {
                // Page is way off-screen to the right
                page.alpha = 0f
            }
        }
    }
}
