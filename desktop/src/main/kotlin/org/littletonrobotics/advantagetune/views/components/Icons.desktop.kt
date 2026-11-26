// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons of the parts of the app that only exist on desktop, from Material Icons. */
object DesktopIcons {
  val Close: ImageVector by lazy {
    icon(
      "Close",
      "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 " +
        "19,17.59 13.41,12z",
    )
  }

  val Pin: ImageVector by lazy {
    icon(
      "Pin",
      "M14,4v5c0,1.12,0.37,2.16,1,3H9c0.65,-0.86,1,-1.9,1,-3V4H14 M17,2H7C6.45,2,6,2.45,6,3" +
        "c0,0.55,0.45,1,1,1l1,0v5c0,1.66,-1.34,3,-3,3v2h5.97v7l1,1l1,-1v-7H19v-2" +
        "c-1.66,0,-3,-1.34,-3,-3V4l1,0c0.55,0,1,-0.45,1,-1C18,2.45,17.55,2,17,2z",
    )
  }

  val PinFilled: ImageVector by lazy {
    icon(
      "PinFilled",
      "M16,9V4l1,0c0.55,0,1,-0.45,1,-1c0,-0.55,-0.45,-1,-1,-1H7C6.45,2,6,2.45,6,3" +
        "c0,0.55,0.45,1,1,1l1,0v5c0,1.66,-1.34,3,-3,3v2h5.97v7l1,1l1,-1v-7H19v-2" +
        "C17.34,12,16,10.66,16,9z",
    )
  }

  val Fullscreen: ImageVector by lazy {
    icon(
      "Fullscreen",
      "M7,14H5v5h5v-2H7v-3zm-2,-4h2V7h3V5H5v5zm12,7h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z",
    )
  }

  val FullscreenExit: ImageVector by lazy {
    icon(
      "FullscreenExit",
      "M5,16h3v3h2v-5H5v2zm3,-8H5v2h5V5H8v3zm6,11h2v-3h3v-2h-5v5zm2,-11V5h-2v5h5V8h-3z",
    )
  }

  private fun icon(name: String, pathData: String): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
      )
      .build()
  }
}

/** An icon button for navigation bars, like `BarButton`, with one of the desktop icons. */
@Composable
fun IconBarButton(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = AppTheme.colors.accent,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      modifier
        .size(44.dp)
        .clip(CircleShape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(),
          onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(24.dp),
    )
  }
}
