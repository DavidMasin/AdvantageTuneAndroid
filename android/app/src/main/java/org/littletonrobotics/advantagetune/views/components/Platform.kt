// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The views are shared with the desktop app. This file holds what they need from the platform, as
// implemented on Android; the desktop app has its own implementation of each declaration.

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
@ReadOnlyComposable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String {
  return androidx.compose.ui.res.stringResource(id, *formatArgs)
}

@Composable
fun painterResource(@DrawableRes id: Int): Painter = androidx.compose.ui.res.painterResource(id)

/** Calls `onBack` when the user navigates back. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

/** Returns an action that copies text to the clipboard. */
@Composable
fun rememberClipboardCopy(): (String) -> Unit {
  val context = LocalContext.current
  return remember(context) {
    { text ->
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    }
  }
}

/** Returns an action that shares text with another app. */
@Composable
fun rememberShareAction(): (String) -> Unit {
  val context = LocalContext.current
  return remember(context) {
    { text ->
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, text)
        }
      context.startActivity(Intent.createChooser(intent, null))
    }
  }
}

/** Returns a formatter for the time of day with seconds, honoring the system's 24-hour setting. */
@Composable
fun rememberTimeFormatter(): (Date) -> String {
  val context = LocalContext.current
  return remember(context) {
    { date ->
      val locale = Locale.getDefault()
      val skeleton = if (DateFormat.is24HourFormat(context)) "Hms" else "hms"
      SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
    }
  }
}

/**
 * Lets content be scrolled by dragging it on platforms where scrolling containers do not already
 * follow the finger. Android's do, so this does nothing here.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.dragToScroll(state: ScrollableState, orientation: Orientation): Modifier = this

/** Reports horizontal drags, for rows that reveal actions when swiped. */
@Composable
fun Modifier.swipeGesture(
  onDelta: (delta: Float) -> Unit,
  onStopped: suspend (velocity: Float) -> Unit,
): Modifier {
  return draggable(
    orientation = Orientation.Horizontal,
    state = rememberDraggableState(onDelta),
    onDragStopped = { velocity -> onStopped(velocity) },
  )
}

/**
 * Calls `onClick` for a click with the secondary mouse button, which opens context menus on
 * desktop. Android has no such click, so this does nothing here.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.onSecondaryClick(onClick: (position: Offset) -> Unit): Modifier = this
