// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import java.util.Locale
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.Resources

/** Localized strings used outside of views, the counterpart of `String(localized:)` on iOS. */
object Localized {
  fun bytes(count: Int): String {
    return Resources.string(R.string.bytes_count, count)
  }

  fun noTunedValues(): String {
    return Resources.string(R.string.no_tuned_values)
  }

  /** Formats a count the way integers are shown within localized text. */
  fun count(value: Int): String {
    return String.format(Locale.getDefault(Locale.Category.FORMAT), "%,d", value)
  }
}
