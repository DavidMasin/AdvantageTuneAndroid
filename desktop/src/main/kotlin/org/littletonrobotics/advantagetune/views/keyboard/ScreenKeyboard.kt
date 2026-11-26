// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.keyboard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextRange
import org.littletonrobotics.advantagetune.TouchScreen
import org.littletonrobotics.advantagetune.models.UserDefaults

/** A text field that the on-screen keyboard types into. */
class KeyboardTarget(
  val state: TextFieldState,
  /** Whether the field holds a number, which is typed on a number pad. */
  val isNumeric: Boolean,
  val onSubmit: () -> Unit,
) {
  /** Types text at the cursor, in place of the selection. */
  fun type(text: String) {
    state.edit {
      val start = selection.min
      replace(start, selection.max, text)
      selection = TextRange(start + text.length)
    }
  }

  /** Removes the selection, or the character before the cursor. */
  fun deleteBackward() {
    state.edit {
      if (!selection.collapsed) {
        val start = selection.min
        replace(start, selection.max, "")
        selection = TextRange(start)
      } else if (selection.min > 0) {
        val end = selection.min
        // Both halves of a character outside the basic plane go together
        val isPair = end >= 2 && Character.isLowSurrogate(charAt(end - 1))
        val start = if (isPair) end - 2 else end - 1
        replace(start, end, "")
        selection = TextRange(start)
      }
    }
  }

  fun clear() {
    state.clearText()
  }

  /** Changes the sign of a number, wherever the cursor is. */
  fun toggleSign() {
    state.edit {
      val cursor = selection
      if (length > 0 && charAt(0) == '-') {
        replace(0, 1, "")
        selection = TextRange(maxOf(cursor.start - 1, 0), maxOf(cursor.end - 1, 0))
      } else {
        replace(0, 0, "-")
        selection = TextRange(cursor.start + 1, cursor.end + 1)
      }
    }
  }
}

/**
 * The keyboard that the app shows itself. Windows does not open its touch keyboard for the text
 * fields of this app, so on a touch screen there would be no way to type.
 */
class ScreenKeyboard {
  /** Whether the keyboard is shown when a text field is focused. The user can turn it off. */
  var isEnabled: Boolean by
    mutableStateOf(
      when (UserDefaults.string(enabledKey)) {
        "on" -> true
        "off" -> false
        // A computer with a touch screen may have no other keyboard
        else -> TouchScreen.isPresent
      }
    )
    private set

  /** The focused text field. */
  var target: KeyboardTarget? by mutableStateOf(null)

  fun toggle() {
    isEnabled = !isEnabled
    UserDefaults.set(if (isEnabled) "on" else "off", enabledKey)
  }

  private companion object {
    const val enabledKey = "screen_keyboard"
  }
}

val LocalScreenKeyboard = staticCompositionLocalOf<ScreenKeyboard?> { null }
