package com.asdk.tools.wpstatussaver.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager

class GridPinchZoomGestureListener(
    private val recyclerView: RecyclerView,
    private val swipeRefreshLayout: SwipeRefreshLayout? = null,
    private val onSpanChanged: (Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isScaling = false
    private var cumulativeScale = 1.0f

    private val scaleGestureDetector = ScaleGestureDetector(
        recyclerView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                cumulativeScale = 1.0f
                recyclerView.pivotX = detector.focusX
                recyclerView.pivotY = detector.focusY
                recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                swipeRefreshLayout?.isEnabled = false
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                cumulativeScale *= scaleFactor
                cumulativeScale = cumulativeScale.coerceIn(0.68f, 1.45f)

                recyclerView.pivotX = detector.focusX
                recyclerView.pivotY = detector.focusY

                // Live interactive scaling during the pinch gesture
                recyclerView.scaleX = cumulativeScale
                recyclerView.scaleY = cumulativeScale

                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false

                val gridLayoutManager = recyclerView.layoutManager as? GridLayoutManager
                val currentSpan = gridLayoutManager?.spanCount ?: 2
                var targetSpan = currentSpan

                // Pinch-in (scale < 0.88) -> More columns (3 columns)
                if (cumulativeScale < 0.88f && currentSpan < 3) {
                    targetSpan = 3
                }
                // Pinch-out (scale > 1.12) -> Fewer columns (2 columns)
                else if (cumulativeScale > 1.12f && currentSpan > 2) {
                    targetSpan = 2
                }

                // Smoothly spring scale back to 1.0f
                val animX = ObjectAnimator.ofFloat(recyclerView, "scaleX", recyclerView.scaleX, 1.0f)
                val animY = ObjectAnimator.ofFloat(recyclerView, "scaleY", recyclerView.scaleY, 1.0f)
                AnimatorSet().apply {
                    playTogether(animX, animY)
                    duration = 180
                    interpolator = DecelerateInterpolator()
                    start()
                }

                if (targetSpan != currentSpan && gridLayoutManager != null) {
                    // Google Photos-like smooth animated transition between grid columns
                    TransitionManager.beginDelayedTransition(
                        recyclerView,
                        AutoTransition().apply {
                            duration = 240
                            interpolator = FastOutSlowInInterpolator()
                        }
                    )
                    gridLayoutManager.spanCount = targetSpan
                    SettingsManager.setGridColumns(recyclerView.context, targetSpan)
                    HapticHelper.selection(recyclerView)
                    onSpanChanged(targetSpan)
                }

                cumulativeScale = 1.0f
                swipeRefreshLayout?.isEnabled = true
                recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    )

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (e.pointerCount >= 2) {
            rv.parent?.requestDisallowInterceptTouchEvent(true)
            swipeRefreshLayout?.isEnabled = false
            scaleGestureDetector.onTouchEvent(e)
            return isScaling
        }

        val action = e.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isScaling) {
                isScaling = false
                swipeRefreshLayout?.isEnabled = true
                rv.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        if (isScaling) {
            scaleGestureDetector.onTouchEvent(e)
            return true
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        val action = e.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isScaling) {
                isScaling = false
                swipeRefreshLayout?.isEnabled = true
                rv.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        scaleGestureDetector.onTouchEvent(e)
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
