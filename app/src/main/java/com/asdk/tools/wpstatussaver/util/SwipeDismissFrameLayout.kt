package com.asdk.tools.wpstatussaver.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 2
    private var velocityTracker: VelocityTracker? = null

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var isAnimating = false

    var isZoomedPredicate: () -> Boolean = { false }
    var onDismiss: (() -> Unit)? = null
    var viewsToFade: List<View> = emptyList()
    var backgroundView: View? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isAnimating) return true
        if (isZoomedPredicate()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dy = ev.rawY - downY
                val dx = ev.rawX - downX
                if (!isDragging && abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return isDragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return true
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (!isDragging && abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (isDragging) {
                    applyTranslation(dy)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val yVelocity = velocityTracker?.yVelocity ?: 0f
                    val dy = event.rawY - downY
                    val dismissThreshold = height * 0.16f

                    if (abs(dy) > dismissThreshold || abs(yVelocity) > minFlingVelocity) {
                        animateDismiss(dy, yVelocity)
                    } else {
                        animateRestore()
                    }
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyTranslation(dy: Float) {
        val child = getChildAt(0) ?: this
        child.translationY = dy * 0.72f

        val totalH = height.toFloat().coerceAtLeast(1f)
        val progress = (abs(dy) / (totalH * 0.4f)).coerceIn(0f, 1f)

        val scale = (1f - progress * 0.18f).coerceIn(0.82f, 1f)
        child.scaleX = scale
        child.scaleY = scale

        val bgAlpha = (1f - progress * 0.85f).coerceIn(0f, 1f)
        backgroundView?.alpha = bgAlpha

        for (v in viewsToFade) {
            v.alpha = (1f - progress * 2.2f).coerceIn(0f, 1f)
        }
    }

    private fun animateDismiss(dy: Float, yVelocity: Float) {
        isAnimating = true
        val child = getChildAt(0) ?: this
        val extraOffset = 40 * resources.displayMetrics.density
        val targetY = child.translationY + if (dy > 0 || yVelocity > 0) extraOffset else -extraOffset

        child.animate()
            .translationY(targetY)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onDismiss?.invoke()
                }
            })
            .start()

        backgroundView?.animate()?.alpha(0f)?.setDuration(150)?.start()
        for (v in viewsToFade) {
            v.animate().alpha(0f).setDuration(80).start()
        }
    }

    private fun animateRestore() {
        isAnimating = true
        val child = getChildAt(0) ?: this
        child.animate()
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

        backgroundView?.animate()?.alpha(1f)?.setDuration(220)?.start()
        for (v in viewsToFade) {
            v.animate().alpha(1f).setDuration(220).start()
        }
    }
}
