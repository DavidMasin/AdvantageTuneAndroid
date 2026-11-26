// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
import org.littletonrobotics.advantagetune.Resources

// The views are shared with the Android app. This file holds what they need from the platform, as
// implemented on desktop.

@Composable fun stringResource(id: Int): String = Resources.string(id)

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = Resources.string(id, *formatArgs)

@Composable
fun painterResource(id: Int): Painter {
  val density = LocalDensity.current
  return rememberVectorPainter(remember(id, density) { Resources.vector(id, density) })
}

// MARK: - Back Navigation

/** Receives the requests to navigate back, which come from the Escape key. */
class BackDispatcher {
  private val handlers = mutableStateListOf<() -> Unit>()

  /** Calls the innermost handler, and returns whether there was one. */
  fun navigateBack(): Boolean {
    val handler = handlers.lastOrNull() ?: return false
    handler()
    return true
  }

  internal fun add(handler: () -> Unit) {
    handlers.add(handler)
  }

  internal fun remove(handler: () -> Unit) {
    handlers.remove(handler)
  }
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

/** Calls `onBack` when the user navigates back. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  val dispatcher = LocalBackDispatcher.current
  val currentOnBack by rememberUpdatedState(onBack)
  if (enabled) {
    DisposableEffect(dispatcher) {
      val handler = { currentOnBack() }
      dispatcher.add(handler)
      onDispose { dispatcher.remove(handler) }
    }
  }
}

// MARK: - Clipboard, Sharing and Time

/** Shows a short message to the user. Provided by the window. */
val LocalMessagePresenter = staticCompositionLocalOf<(String) -> Unit> { {} }

private fun copyToClipboard(text: String) {
  Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/** Returns an action that copies text to the clipboard. */
@Composable
fun rememberClipboardCopy(): (String) -> Unit {
  return remember { { text -> copyToClipboard(text) } }
}

/**
 * Returns an action that shares text. Desktop has no share sheet, so the text is copied to the
 * clipboard to be pasted into another app.
 */
@Composable
fun rememberShareAction(): (String) -> Unit {
  val showMessage = LocalMessagePresenter.current
  return remember(showMessage) {
    { text ->
      copyToClipboard(text)
      showMessage("Copied to clipboard")
    }
  }
}

/** Returns a formatter for the time of day with seconds. */
@Composable
fun rememberTimeFormatter(): (Date) -> String {
  return remember {
    val format = DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.getDefault());
    { date -> format.format(date) }
  }
}

// MARK: - Touch Gestures

// Windows delivers touches to desktop apps as mouse events. Scrolling containers do not follow a
// dragged mouse, and a mouse counts as dragged after the slightest movement, which a finger
// always makes. The gestures below treat the mouse like a finger instead: content scrolls when it
// is dragged, and only after the pointer has moved a noticeable distance.
//
// The pointer is watched on the initial pass, before the content under it. A drag can then start
// on a text field, which would otherwise use it to select text.
private val DragSlop = 12.dp

/**
 * Waits until the pointer has moved past the slop, mostly along the given orientation. Returns the
 * distance past the slop, or null if the pointer was released or another gesture took over.
 */
private suspend fun AwaitPointerEventScope.awaitDragStart(
  pointerId: PointerId,
  orientation: Orientation,
  slop: Float,
  tracker: VelocityTracker,
): Float? {
  var along = 0f
  var across = 0f
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
    if (!change.pressed || change.isConsumed) return null
    tracker.addPosition(change.uptimeMillis, change.position)

    val movement = change.positionChange()
    along += if (orientation == Orientation.Vertical) movement.y else movement.x
    across += if (orientation == Orientation.Vertical) movement.x else movement.y
    if (abs(along) > slop || abs(across) > slop) {
      if (abs(along) <= abs(across)) return null
      change.consume()
      return along - sign(along) * slop
    }
  }
}

/** Reports drags along the orientation that start with the primary mouse button. */
private fun Modifier.touchLikeDrag(
  orientation: Orientation,
  onDelta: (Float) -> Unit,
  onStopped: (velocity: Float) -> Unit,
): Modifier = composed {
  val currentOnDelta by rememberUpdatedState(onDelta)
  val currentOnStopped by rememberUpdatedState(onStopped)

  pointerInput(orientation) {
    val slop = DragSlop.toPx()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture

      val tracker = VelocityTracker()
      tracker.addPosition(down.uptimeMillis, down.position)
      val overSlop = awaitDragStart(down.id, orientation, slop, tracker) ?: return@awaitEachGesture
      currentOnDelta(overSlop)

      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        tracker.addPosition(change.uptimeMillis, change.position)
        val movement = change.positionChange()
        currentOnDelta(if (orientation == Orientation.Vertical) movement.y else movement.x)
        change.consume()
      }

      val velocity = tracker.calculateVelocity()
      currentOnStopped(if (orientation == Orientation.Vertical) velocity.y else velocity.x)
    }
  }
}

/** Lets content be scrolled by dragging it, with a fling when it is released. */
fun Modifier.dragToScroll(state: ScrollableState, orientation: Orientation): Modifier = composed {
  val scope = rememberCoroutineScope()
  val flingBehavior = ScrollableDefaults.flingBehavior()
  // Horizontal content runs from right to left in right-to-left layouts
  val isReversed =
    orientation == Orientation.Horizontal && LocalLayoutDirection.current == LayoutDirection.Rtl
  val direction = if (isReversed) 1f else -1f

  touchLikeDrag(
    orientation = orientation,
    onDelta = { delta -> state.dispatchRawDelta(delta * direction) },
    onStopped = { velocity ->
      scope.launch { state.scroll { with(flingBehavior) { performFling(velocity * direction) } } }
    },
  )
}

/** Reports horizontal drags, for rows that reveal actions when swiped. */
@Composable
fun Modifier.swipeGesture(
  onDelta: (delta: Float) -> Unit,
  onStopped: suspend (velocity: Float) -> Unit,
): Modifier {
  val scope = rememberCoroutineScope()
  return touchLikeDrag(
    orientation = Orientation.Horizontal,
    onDelta = onDelta,
    onStopped = { velocity -> scope.launch { onStopped(velocity) } },
  )
}

/**
 * Calls `onClick` for a click with the secondary mouse button, which opens context menus. Windows
 * also sends this click when a finger is held on a touch screen.
 */
fun Modifier.onSecondaryClick(onClick: (position: Offset) -> Unit): Modifier = composed {
  val currentOnClick by rememberUpdatedState(onClick)
  pointerInput(Unit) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent()
        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
          val change = event.changes.first()
          change.consume()
          currentOnClick(change.position)
        }
      }
    }
  }
}
