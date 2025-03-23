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
 * Enhanced utility for handling haptic feedback consistently across the app
 * with improved feedback types and durations
 */
object HapticFeedback {

    enum class FeedbackType {
        LIGHT,     // For subtle interactions like tab changes, list item selection
        MEDIUM,    // For button presses, selections
        SUCCESS,   // For confirmation of success
        ERROR,     // For error states
        HEAVY      // For important actions like confirmation dialogs
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

        private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        fun performHapticFeedback(feedbackType: FeedbackType) {
            // Attempt view-based feedback first as it's often more reliable
            val viewResult = when (feedbackType) {
                FeedbackType.LIGHT -> view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                FeedbackType.MEDIUM -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                FeedbackType.SUCCESS -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                FeedbackType.ERROR -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                FeedbackType.HEAVY -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            // Use vibrator as a fallback or to enhance the feedback on newer devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                when (feedbackType) {
                    FeedbackType.LIGHT -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(10, 50))
                    }
                    FeedbackType.MEDIUM -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(20, 80))
                    }
                    FeedbackType.SUCCESS -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                        } else {
                            // Double pulse for success
                            val timings = longArrayOf(0, 30, 60, 30)
                            val amplitudes = intArrayOf(0, 80, 0, 120)
                            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                        }
                    }
                    FeedbackType.ERROR -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                        } else {
                            // Triple pulse for error
                            val timings = longArrayOf(0, 30, 50, 30, 50, 30)
                            val amplitudes = intArrayOf(0, 120, 0, 120, 0, 120)
                            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                        }
                    }
                    FeedbackType.HEAVY -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(40, 255))
                    }
                }
            } else if (vibrator?.hasVibrator() == true) {
                // Fallback for older devices
                when (feedbackType) {
                    FeedbackType.LIGHT -> vibrator.vibrate(10)
                    FeedbackType.MEDIUM -> vibrator.vibrate(20)
                    FeedbackType.SUCCESS -> vibrator.vibrate(40)
                    FeedbackType.ERROR -> vibrator.vibrate(longArrayOf(0, 30, 50, 30, 50, 30), -1)
                    FeedbackType.HEAVY -> vibrator.vibrate(60)
                }
            }
        }
    }
}