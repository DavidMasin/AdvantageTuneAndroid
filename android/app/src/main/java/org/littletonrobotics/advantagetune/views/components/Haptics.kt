// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import java.lang.ref.WeakReference

object Haptics {
  enum class ImpactStyle {
    Light,
    Medium,
  }

  enum class NotificationType {
    Success,
    Warning,
  }

  private var view: WeakReference<View>? = null

  /** Sets the view that performs the feedback. */
  fun attach(view: View) {
    this.view = WeakReference(view)
  }

  /** Feedback for changing selections (e.g. filter pills, tabs). */
  fun selection() {
    perform(HapticFeedbackConstants.CLOCK_TICK)
  }

  /** Feedback for physical impacts (e.g. steppers, buttons, insertions). */
  fun impact(style: ImpactStyle = ImpactStyle.Light) {
    when (style) {
      ImpactStyle.Light -> perform(HapticFeedbackConstants.KEYBOARD_TAP)
      ImpactStyle.Medium -> perform(HapticFeedbackConstants.CONTEXT_CLICK)
    }
  }

  /** Feedback for discrete task outcomes (e.g. connected, warning, revert, delete). */
  fun notification(type: NotificationType) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      when (type) {
        NotificationType.Success -> perform(HapticFeedbackConstants.CONFIRM)
        NotificationType.Warning -> perform(HapticFeedbackConstants.REJECT)
      }
    } else {
      when (type) {
        NotificationType.Success -> perform(HapticFeedbackConstants.VIRTUAL_KEY)
        NotificationType.Warning -> perform(HapticFeedbackConstants.LONG_PRESS)
      }
    }
  }

  private fun perform(feedbackConstant: Int) {
    view?.get()?.performHapticFeedback(feedbackConstant)
  }
}
