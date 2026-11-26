// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.DesktopIcons
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.weight

private val KeyHeight = 52.dp
private val KeySpacing = 6.dp

private enum class KeyKind {
  /** Types a character. */
  Character,
  /** Edits the text in another way. */
  Function,
  /** Ends the editing. */
  Done,
}

/** Shows the keyboard at the bottom of the window while a text field is focused. */
@Composable
fun ScreenKeyboardView(keyboard: ScreenKeyboard, modifier: Modifier = Modifier) {
  val target = keyboard.target
  if (!keyboard.isEnabled || target == null) return

  val colors = AppTheme.colors
  val focusManager = LocalFocusManager.current
  val done = {
    target.onSubmit()
    focusManager.clearFocus()
  }

  Column(modifier = modifier.fillMaxWidth().background(colors.systemGray5)) {
    Separator()
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
      if (target.isNumeric) {
        NumberPad(target = target, onDone = done)
      } else {
        TextKeys(target = target, onDone = done)
      }
    }
  }
}

// MARK: - Number Pad

@Composable
private fun NumberPad(target: KeyboardTarget, onDone: () -> Unit) {
  KeyRows(maxWidth = 440.dp) {
    KeyRow {
      for (digit in "789") CharacterKey(digit, target)
      BackspaceKey(target)
    }
    KeyRow {
      for (digit in "456") CharacterKey(digit, target)
      Key(label = "Clear", kind = KeyKind.Function, tag = "clear") { target.clear() }
    }
    KeyRow {
      for (digit in "123") CharacterKey(digit, target)
      Key(label = "+/−", kind = KeyKind.Function, tag = "sign") { target.toggleSign() }
    }
    KeyRow {
      CharacterKey('0', target, weight = 2f)
      CharacterKey('.', target)
      DoneKey(onDone)
    }
  }
}

// MARK: - Text Keys

@Composable
private fun TextKeys(target: KeyboardTarget, onDone: () -> Unit) {
  // Capital letters are typed one at a time
  var isShifted by remember { mutableStateOf(false) }

  @Composable
  fun RowScope.LetterKeys(letters: String) {
    for (letter in letters) {
      val character = if (isShifted) letter.uppercaseChar() else letter
      Key(label = character.toString(), kind = KeyKind.Character, tag = letter.toString()) {
        target.type(character.toString())
        isShifted = false
      }
    }
  }

  KeyRows(maxWidth = 920.dp) {
    KeyRow {
      for (digit in "1234567890") CharacterKey(digit, target)
      BackspaceKey(target)
    }
    KeyRow {
      LetterKeys("qwertyuiop")
      CharacterKey('/', target)
    }
    KeyRow {
      Spacer(modifier = Modifier.weight(0.5f))
      LetterKeys("asdfghjkl")
      CharacterKey('-', target)
      Spacer(modifier = Modifier.weight(0.5f))
    }
    KeyRow {
      Key(
        icon = DesktopIcons.Shift,
        contentDescription = "Shift",
        kind = if (isShifted) KeyKind.Done else KeyKind.Function,
        tag = "shift",
      ) {
        isShifted = !isShifted
      }
      LetterKeys("zxcvbnm")
      CharacterKey('.', target)
      CharacterKey('_', target)
      CharacterKey(':', target)
    }
    KeyRow {
      Key(label = "Clear", kind = KeyKind.Function, tag = "clear", weight = 2f) { target.clear() }
      Key(
        label = "",
        contentDescription = "Space",
        kind = KeyKind.Character,
        tag = "space",
        weight = 6f,
      ) {
        target.type(" ")
      }
      DoneKey(onDone, weight = 3f)
    }
  }
}

// MARK: - Keys

@Composable
private fun KeyRows(maxWidth: Dp, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KeySpacing),
  ) {
    content()
  }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(KeySpacing),
    content = content,
  )
}

@Composable
private fun RowScope.CharacterKey(character: Char, target: KeyboardTarget, weight: Float = 1f) {
  Key(
    label = character.toString(),
    kind = KeyKind.Character,
    tag = character.toString(),
    weight = weight,
  ) {
    target.type(character.toString())
  }
}

@Composable
private fun RowScope.BackspaceKey(target: KeyboardTarget) {
  Key(
    icon = DesktopIcons.Backspace,
    contentDescription = "Delete",
    kind = KeyKind.Function,
    tag = "backspace",
  ) {
    target.deleteBackward()
  }
}

@Composable
private fun RowScope.DoneKey(onDone: () -> Unit, weight: Float = 1f) {
  Key(
    label = stringResource(R.string.done),
    kind = KeyKind.Done,
    tag = "done",
    weight = weight,
    onPress = onDone,
  )
}

/**
 * A key of the keyboard. A key must not take the focus from the text field when it is pressed, so
 * it is not a button.
 */
@Composable
private fun RowScope.Key(
  label: String? = null,
  icon: ImageVector? = null,
  contentDescription: String? = label,
  kind: KeyKind,
  tag: String,
  weight: Float = 1f,
  onPress: () -> Unit,
) {
  val colors = AppTheme.colors
  val currentOnPress by rememberUpdatedState(onPress)
  var isPressed by remember { mutableStateOf(false) }

  val background =
    when (kind) {
      KeyKind.Character -> colors.secondarySystemGroupedBackground
      KeyKind.Function -> colors.gray.copy(alpha = 0.35f).compositeOver(colors.systemGray5)
      KeyKind.Done -> colors.accent
    }
  val contentColor = if (kind == KeyKind.Done) Color.White else colors.label

  Box(
    modifier =
      Modifier.weight(weight)
        .height(KeyHeight)
        .clip(RoundedCornerShape(8.dp))
        .background(
          if (isPressed) colors.gray.copy(alpha = 0.4f).compositeOver(background) else background
        )
        .testTag("key-$tag")
        .semantics(mergeDescendants = true) {
          role = Role.Button
          if (contentDescription != null) this.contentDescription = contentDescription
          onClick {
            currentOnPress()
            true
          }
        }
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
              isPressed = true
              tryAwaitRelease()
              isPressed = false
            },
            onTap = { currentOnPress() },
          )
        },
    contentAlignment = Alignment.Center,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(24.dp),
      )
    } else if (label != null) {
      val isWord = label.length > 1
      Text(
        text = label,
        style = if (isWord) AppType.body.weight(FontWeight.Medium) else AppType.title2,
        color = contentColor,
        maxLines = 1,
      )
    }
  }
}
