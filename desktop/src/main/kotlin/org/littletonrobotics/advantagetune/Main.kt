// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.littletonrobotics.advantagetune.models.UserDefaults
import org.littletonrobotics.advantagetune.views.MainTabView
import org.littletonrobotics.advantagetune.views.MainViewModel
import org.littletonrobotics.advantagetune.views.components.AdvantageTuneTheme
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.BackDispatcher
import org.littletonrobotics.advantagetune.views.components.LocalBackDispatcher
import org.littletonrobotics.advantagetune.views.components.LocalMessagePresenter

fun main() {
  UserDefaults.initialize()

  application {
    val windowState = remember { WindowPlacementStore.initialState() }
    val icon = remember {
      val image = ImageIO.read(Thread.currentThread().contextClassLoader.getResource("icon.png"))
      BitmapPainter(image.toComposeImageBitmap())
    }
    val backDispatcher = remember { BackDispatcher() }

    // Remembers where the window is, so it opens on the same screen next time
    LaunchedEffect(windowState) {
      snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }
        .collectLatest {
          delay(500)
          WindowPlacementStore.save(windowState)
        }
    }

    Window(
      onCloseRequest = {
        WindowPlacementStore.save(windowState)
        exitApplication()
      },
      state = windowState,
      title = "AdvantageTune",
      icon = icon,
      onPreviewKeyEvent = { event ->
        if (event.type != KeyEventType.KeyDown) {
          false
        } else if (event.key == Key.Escape) {
          backDispatcher.navigateBack()
        } else if (event.key == Key.F11) {
          windowState.placement =
            if (windowState.placement == WindowPlacement.Fullscreen) {
              WindowPlacement.Maximized
            } else {
              WindowPlacement.Fullscreen
            }
          true
        } else {
          false
        }
      },
    ) {
      window.minimumSize = Dimension(360, 480)

      val viewModel = remember { MainViewModel() }
      DesktopApp(viewModel = viewModel, backDispatcher = backDispatcher)
    }
  }
}

/** The content of the window. */
@Composable
fun DesktopApp(viewModel: MainViewModel, backDispatcher: BackDispatcher) {
  var message by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(message) {
    if (message != null) {
      delay(2000)
      message = null
    }
  }

  val isRightToLeft = !ComponentOrientation.getOrientation(Locale.getDefault()).isLeftToRight
  CompositionLocalProvider(
    LocalBackDispatcher provides backDispatcher,
    LocalMessagePresenter provides { text -> message = text },
    LocalLayoutDirection provides if (isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
  ) {
    AdvantageTuneTheme {
      Box(modifier = Modifier.fillMaxSize()) {
        MainTabView(viewModel = viewModel)

        message?.let { MessageView(text = it, modifier = Modifier.align(Alignment.BottomCenter)) }
      }
    }
  }
}

/** A short message shown above the content. */
@Composable
private fun MessageView(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = AppType.subheadline,
    color = Color.White,
    modifier =
      modifier
        .padding(bottom = 96.dp)
        .clip(CircleShape)
        .background(AppTheme.colors.gray)
        .padding(horizontal = 16.dp, vertical = 10.dp),
  )
}

/** Stores the placement of the window between launches. */
private object WindowPlacementStore {
  private const val placementKey = "window_placement"
  private const val xKey = "window_x"
  private const val yKey = "window_y"
  private const val widthKey = "window_width"
  private const val heightKey = "window_height"

  private val defaultSize = DpSize(480.dp, 860.dp)

  /**
   * Returns where the window opens: where it was last time if that screen is still connected,
   * otherwise maximized on the touch screen if there is one, otherwise centered.
   */
  fun initialState(): WindowState {
    val x = UserDefaults.double(xKey)
    val y = UserDefaults.double(yKey)
    val width = UserDefaults.double(widthKey)
    val height = UserDefaults.double(heightKey)
    if (x != null && y != null && width != null && height != null) {
      val bounds = Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
      if (width >= 200 && height >= 200 && isOnScreen(bounds)) {
        val placement =
          when (UserDefaults.string(placementKey)) {
            "maximized" -> WindowPlacement.Maximized
            "fullscreen" -> WindowPlacement.Fullscreen
            else -> WindowPlacement.Floating
          }
        return WindowState(
          placement = placement,
          position = WindowPosition(x.dp, y.dp),
          size = DpSize(width.dp, height.dp),
        )
      }
    }

    val touchScreen = TouchScreen.findBounds()
    if (touchScreen != null) {
      // A window is maximized on the screen that it is on, so it starts inside the touch screen
      val width = minOf(defaultSize.width.value.toInt(), touchScreen.width)
      val height = minOf(defaultSize.height.value.toInt(), touchScreen.height)
      return WindowState(
        placement = WindowPlacement.Maximized,
        position =
          WindowPosition(
            (touchScreen.x + (touchScreen.width - width) / 2).dp,
            (touchScreen.y + (touchScreen.height - height) / 2).dp,
          ),
        size = DpSize(width.dp, height.dp),
      )
    }

    // The window has to fit next to the taskbar on a small screen
    val available = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return WindowState(
      position = WindowPosition(Alignment.Center),
      size =
        DpSize(
          minOf(defaultSize.width, available.width.dp),
          minOf(defaultSize.height, available.height.dp),
        ),
    )
  }

  fun save(state: WindowState) {
    UserDefaults.set(
      when (state.placement) {
        WindowPlacement.Maximized -> "maximized"
        WindowPlacement.Fullscreen -> "fullscreen"
        WindowPlacement.Floating -> "floating"
      },
      placementKey,
    )

    // The position and size are those of the window when it is not maximized
    val position = state.position
    if (position is WindowPosition.Absolute && state.size.width.value > 0) {
      UserDefaults.set(position.x.value.toDouble(), xKey)
      UserDefaults.set(position.y.value.toDouble(), yKey)
      UserDefaults.set(state.size.width.value.toDouble(), widthKey)
      UserDefaults.set(state.size.height.value.toDouble(), heightKey)
    }
  }

  /** Returns whether enough of the bounds is on a connected screen to reach the window. */
  private fun isOnScreen(bounds: Rectangle): Boolean {
    return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { device ->
      val visible = device.defaultConfiguration.bounds.intersection(bounds)
      visible.width >= 100 && visible.height >= 100
    }
  }
}
