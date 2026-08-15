package com.asdk.tools.wpstatussaver.util

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.asdk.tools.wpstatussaver.adapter.StatusAdapter
import com.asdk.tools.wpstatussaver.model.StatusMedia

class DragSelectTouchListener(
    private val recyclerView: RecyclerView,
    private val adapterProvider: () -> StatusAdapter?,
    private val onSelectionChange: ((Int) -> Unit)? = null
) : RecyclerView.OnItemTouchListener {

    private var isDragSelecting = false
    private var anchorPosition = RecyclerView.NO_POSITION
    private var lastTouchedPosition = RecyclerView.NO_POSITION
    private var isSelectingState = true
    private val initialSelectedItems = HashSet<StatusMedia>()

    private var autoScrollDistance = 0
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (isDragSelecting && autoScrollDistance != 0) {
                recyclerView.scrollBy(0, autoScrollDistance)
                val currentX = lastMotionX
                val currentY = lastMotionY
                if (currentX != -1f && currentY != -1f) {
                    processPositionUnderTouch(currentX, currentY)
                }
                recyclerView.postOnAnimation(this)
            }
        }
    }

    private var lastMotionX = -1f
    private var lastMotionY = -1f

    fun startDragSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val adapter = adapterProvider() ?: return

        isDragSelecting = true
        anchorPosition = position
        lastTouchedPosition = position
        initialSelectedItems.clear()
        initialSelectedItems.addAll(adapter.selectedItems)

        // If the item at anchor was already selected, we keep selecting; if not, we select it
        val item = adapter.currentList.getOrNull(position)
        isSelectingState = true

        if (!adapter.isSelectionMode) {
            adapter.enterSelectionMode(item)
        } else if (item != null && !adapter.selectedItems.contains(item)) {
            adapter.selectedItems.add(item)
            adapter.notifyItemChanged(position)
            onSelectionChange?.invoke(adapter.selectedItems.size)
        }

        try {
            recyclerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (e: Exception) {
            // Ignore if haptics unavailable
        }
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (!isDragSelecting) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                handleMove(e)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishDrag()
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (!isDragSelecting) return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> handleMove(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag()
        }
    }

    private fun handleMove(e: MotionEvent) {
        lastMotionX = e.x
        lastMotionY = e.y
        processPositionUnderTouch(e.x, e.y)

        // iOS-style auto-scroll near edges
        val height = recyclerView.height
        val edgeThreshold = (height * 0.16f).toInt().coerceAtLeast(60)
        val y = e.y.toInt()

        autoScrollDistance = when {
            y < edgeThreshold -> {
                val factor = (edgeThreshold - y).toFloat() / edgeThreshold
                -((factor * 25).toInt().coerceAtLeast(6))
            }
            y > height - edgeThreshold -> {
                val factor = (y - (height - edgeThreshold)).toFloat() / edgeThreshold
                ((factor * 25).toInt().coerceAtLeast(6))
            }
            else -> 0
        }

        if (autoScrollDistance != 0) {
            recyclerView.removeCallbacks(autoScrollRunnable)
            recyclerView.postOnAnimation(autoScrollRunnable)
        } else {
            recyclerView.removeCallbacks(autoScrollRunnable)
        }
    }

    private fun processPositionUnderTouch(x: Float, y: Float) {
        val childView = recyclerView.findChildViewUnder(x, y) ?: return
        val position = recyclerView.getChildAdapterPosition(childView)
        if (position != RecyclerView.NO_POSITION && position != lastTouchedPosition) {
            lastTouchedPosition = position
            updateSelectionForRange(anchorPosition, position)
            try {
                recyclerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun updateSelectionForRange(from: Int, to: Int) {
        val adapter = adapterProvider() ?: return
        val items = adapter.currentList
        if (items.isEmpty() || from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return

        val min = from.coerceAtMost(to).coerceAtLeast(0)
        val max = from.coerceAtLeast(to).coerceAtMost(items.size - 1)

        var changed = false
        for (i in 0 until items.size) {
            val item = items[i]
            val shouldBeSelected = if (i in min..max) {
                isSelectingState
            } else {
                initialSelectedItems.contains(item)
            }

            val currentlySelected = adapter.selectedItems.contains(item)
            if (shouldBeSelected != currentlySelected) {
                if (shouldBeSelected) {
                    adapter.selectedItems.add(item)
                } else {
                    adapter.selectedItems.remove(item)
                }
                adapter.notifyItemChanged(i)
                changed = true
            }
        }

        if (changed) {
            onSelectionChange?.invoke(adapter.selectedItems.size)
        }
    }

    private fun finishDrag() {
        isDragSelecting = false
        anchorPosition = RecyclerView.NO_POSITION
        lastTouchedPosition = RecyclerView.NO_POSITION
        lastMotionX = -1f
        lastMotionY = -1f
        autoScrollDistance = 0
        recyclerView.removeCallbacks(autoScrollRunnable)
        initialSelectedItems.clear()
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}
