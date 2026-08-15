package com.asdk.tools.wpstatussaver.util

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GridPinchZoomGestureListener(
    private val recyclerView: RecyclerView,
    private val onSpanChanged: (Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private var isScaling = false
    private var lastSpanChangeTime = 0L

    private val scaleGestureDetector = ScaleGestureDetector(
        recyclerView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpanChangeTime < 400) return true

                val gridLayoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return false
                val currentSpan = gridLayoutManager.spanCount
                val factor = detector.scaleFactor

                // Pinch-in (factor < 0.82) -> More columns (3 columns, smaller items)
                if (factor < 0.82f && currentSpan < 3) {
                    lastSpanChangeTime = currentTime
                    applyNewSpanCount(3)
                    return true
                }

                // Pinch-out (factor > 1.22) -> Fewer columns (2 columns, larger items)
                if (factor > 1.22f && currentSpan > 2) {
                    lastSpanChangeTime = currentTime
                    applyNewSpanCount(2)
                    return true
                }

                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        }
    )

    private fun applyNewSpanCount(newSpan: Int) {
        val gridLayoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (gridLayoutManager.spanCount == newSpan) return

        gridLayoutManager.spanCount = newSpan
        SettingsManager.setGridColumns(recyclerView.context, newSpan)
        recyclerView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onSpanChanged(newSpan)
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (e.pointerCount >= 2) {
            scaleGestureDetector.onTouchEvent(e)
            return isScaling
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        scaleGestureDetector.onTouchEvent(e)
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
