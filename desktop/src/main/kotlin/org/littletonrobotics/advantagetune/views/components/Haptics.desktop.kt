// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

/** Haptic feedback, which desktop computers do not have. */
object Haptics {
  enum class ImpactStyle {
    Light,
    Medium,
  }

  enum class NotificationType {
    Success,
    Warning,
  }

  /** Feedback for changing selections (e.g. filter pills, tabs). */
  fun selection() {}

  /** Feedback for physical impacts (e.g. steppers, buttons, insertions). */
  @Suppress("UNUSED_PARAMETER") fun impact(style: ImpactStyle = ImpactStyle.Light) {}

  /** Feedback for discrete task outcomes (e.g. connected, warning, revert, delete). */
  @Suppress("UNUSED_PARAMETER") fun notification(type: NotificationType) {}
}
