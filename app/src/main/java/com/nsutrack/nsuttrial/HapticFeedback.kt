package com.nsutrack.nsuttrial

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Utility for handling haptic feedback consistently across the app
 */
object HapticFeedback {

    enum class FeedbackType {
        LIGHT, // For subtle interactions like tab changes, list item selection
        MEDIUM, // For button presses, selections
        SUCCESS, // For confirmation of success
        ERROR, // For error states
        HEAVY // For important actions like confirmation dialogs
    }

    /**
     * Performs haptic feedback of different intensities
     */
    @Composable
    fun getHapticFeedback(): HapticHandler {
        val context = LocalContext.current
        val view = LocalView.current
        return HapticHandler(context, view)
    }

    class HapticHandler(private val context: Context, private val view: View) {

        fun performHapticFeedback(feedbackType: FeedbackType) {
            when (feedbackType) {
                FeedbackType.LIGHT -> performLightFeedback()
                FeedbackType.MEDIUM -> performMediumFeedback()
                FeedbackType.SUCCESS -> performSuccessFeedback()
                FeedbackType.ERROR -> performErrorFeedback()
                FeedbackType.HEAVY -> performHeavyFeedback()
            }
        }

        private fun performLightFeedback() {
            // Use view-based haptic feedback as it's more reliable
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        }

        private fun performMediumFeedback() {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        private fun performSuccessFeedback() {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }

        private fun performErrorFeedback() {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }

        private fun performHeavyFeedback() {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}