// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import android.content.Context
import android.content.res.Resources
import java.util.Locale
import org.littletonrobotics.advantagetune.R

/** Localized strings used outside of views, the counterpart of `String(localized:)` on iOS. */
object Localized {
  private var resources: Resources? = null

  fun initialize(context: Context) {
    resources = context.applicationContext.resources
  }

  fun bytes(count: Int): String {
    return resources?.getString(R.string.bytes_count, count) ?: "$count bytes"
  }

  fun noTunedValues(): String {
    return resources?.getString(R.string.no_tuned_values) ?: "No tuned values."
  }

  /** Formats a count the way integers are shown within localized text. */
  fun count(value: Int): String {
    return String.format(Locale.getDefault(Locale.Category.FORMAT), "%,d", value)
  }
}
