package com.asdk.tools.wpstatussaver.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

class SwipeToDismissHelper(
    private val activity: Activity,
    private val targetView: View,
    private val backgroundView: View,
    private val extraViewsToFade: List<View> = emptyList(),
    private val canSwipePredicate: () -> Boolean = { true },
    private val onDismiss: () -> Unit
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val dismissThreshold = 100 * activity.resources.displayMetrics.density

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false
    private var isAnimating = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isAnimating) return true
        if (!canSwipePredicate()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.rawY
                startX = event.rawX
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - startY
                val deltaX = event.rawX - startX

                if (!isDragging) {
                    // Check if movement is primarily vertical and exceeds touch slop
                    if (abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX) * 1.2f) {
                        isDragging = true
                        targetView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isDragging) {
                    applyDrag(deltaY)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val deltaY = event.rawY - startY
                    if (abs(deltaY) > dismissThreshold) {
                        finishWithDismiss(deltaY)
                    } else {
                        cancelAndRestore()
                    }
                    isDragging = false
                    return true
                }
            }
        }
        return false
    }

    private fun applyDrag(deltaY: Float) {
        targetView.translationY = deltaY

        val maxDrag = activity.resources.displayMetrics.heightPixels.toFloat() / 2f
        val progress = (abs(deltaY) / maxDrag).coerceIn(0f, 1f)

        val scale = (1f - progress * 0.25f).coerceIn(0.75f, 1f)
        targetView.scaleX = scale
        targetView.scaleY = scale

        val bgAlpha = (1f - progress * 0.85f).coerceIn(0f, 1f)
        backgroundView.alpha = bgAlpha

        for (view in extraViewsToFade) {
            view.alpha = (1f - progress * 2.5f).coerceIn(0f, 1f)
        }
    }

    private fun finishWithDismiss(deltaY: Float) {
        isAnimating = true
        val targetTranslation = if (deltaY > 0) {
            activity.resources.displayMetrics.heightPixels.toFloat()
        } else {
            -activity.resources.displayMetrics.heightPixels.toFloat()
        }

        targetView.animate()
            .translationY(targetTranslation)
            .scaleX(0.65f)
            .scaleY(0.65f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onDismiss()
                }
            })
            .start()

        backgroundView.animate()
            .alpha(0f)
            .setDuration(180)
            .start()

        for (view in extraViewsToFade) {
            view.animate().alpha(0f).setDuration(120).start()
        }
    }

    private fun cancelAndRestore() {
        isAnimating = true
        targetView.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            .start()

        backgroundView.animate()
            .alpha(1f)
            .setDuration(220)
            .start()

        for (view in extraViewsToFade) {
            view.animate().alpha(1f).setDuration(220).start()
        }
    }
}
