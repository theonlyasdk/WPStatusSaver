package com.asdk.tools.wpstatussaver.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    /**
     * Standard UI element click/tap.
     */
    fun click(view: View?) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
    }

    /**
     * Single selection toggle (checking/unchecking an item).
     * Uses Android 14+ TOGGLE_ON / TOGGLE_OFF if available, falling back to GESTURE_START / KEYBOARD_TAP.
     */
    fun selection(view: View?, isSelected: Boolean = true) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val feedback = when {
            Build.VERSION.SDK_INT >= 34 -> {
                if (isSelected) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                HapticFeedbackConstants.GESTURE_START
            }
            else -> {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        }
        view?.performHapticFeedback(feedback, flags)
    }

    /**
     * Batch selection (e.g. Select All, Deselect All, Range selection).
     * Uses CONFIRM on Android 11+ for a crisp, distinct sensation.
     */
    fun batchSelection(view: View?) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view?.performHapticFeedback(feedback, flags)
    }

    /**
     * Drag-selection rapid tick feedback as finger slides across items.
     * Uses Android 14+ SEGMENT_FREQUENT_TICK / SEGMENT_TICK, Android 11+ CLOCK_TICK.
     */
    fun dragSelectTick(view: View?) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val feedback = when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CLOCK_TICK
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        view?.performHapticFeedback(feedback, flags)
    }

    /**
     * Long-press to initiate selection mode or drag.
     */
    fun longPress(view: View?) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val feedback = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.DRAG_START
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view?.performHapticFeedback(feedback, flags)
    }

    /**
     * Success confirmation for saving/action completed.
     */
    fun success(view: View?) {
        val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view?.performHapticFeedback(feedback, flags)
    }
}
