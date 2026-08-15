package com.asdk.tools.wpstatussaver.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

object HapticHelper {

    fun click(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun selection(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun longPress(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun success(view: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
