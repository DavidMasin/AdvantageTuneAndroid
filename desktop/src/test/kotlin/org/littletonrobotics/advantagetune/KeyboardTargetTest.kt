// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Test
import org.littletonrobotics.advantagetune.views.keyboard.KeyboardTarget

class KeyboardTargetTest {
  private fun target(text: String, selection: TextRange = TextRange(text.length)): KeyboardTarget {
    return KeyboardTarget(TextFieldState(text, selection), isNumeric = true, onSubmit = {})
  }

  private val KeyboardTarget.text: String
    get() = state.text.toString()

  @Test
  fun typesAtCursor() {
    val target = target("15", TextRange(1))
    target.type("2.")
    assertEquals("12.5", target.text)
    assertEquals(TextRange(3), target.state.selection)
  }

  @Test
  fun typingReplacesSelection() {
    val target = target("3.14159", TextRange(0, 7))
    target.type("2")
    assertEquals("2", target.text)
    assertEquals(TextRange(1), target.state.selection)
  }

  @Test
  fun deletesBeforeCursor() {
    val target = target("123", TextRange(2))
    target.deleteBackward()
    assertEquals("13", target.text)
    assertEquals(TextRange(1), target.state.selection)

    target.deleteBackward()
    target.deleteBackward()
    assertEquals("3", target.text)
  }

  @Test
  fun deletesSelection() {
    val target = target("12345", TextRange(1, 4))
    target.deleteBackward()
    assertEquals("15", target.text)
    assertEquals(TextRange(1), target.state.selection)
  }

  @Test
  fun deletesWholeCharacter() {
    val target = target("a🤖")
    target.deleteBackward()
    assertEquals("a", target.text)
  }

  @Test
  fun togglesSignAndKeepsCursor() {
    val target = target("12.5", TextRange(2))
    target.toggleSign()
    assertEquals("-12.5", target.text)
    assertEquals(TextRange(3), target.state.selection)

    target.toggleSign()
    assertEquals("12.5", target.text)
    assertEquals(TextRange(2), target.state.selection)
  }

  @Test
  fun clears() {
    val target = target("12.5")
    target.clear()
    assertEquals("", target.text)
  }
}
