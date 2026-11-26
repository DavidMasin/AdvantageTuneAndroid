// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.stringResource

@Composable
fun LeafEditorContainer(
  label: String?,
  isFocused: Boolean,
  onDelete: (() -> Unit)?,
  onCommit: () -> Unit,
  modifier: Modifier = Modifier,
  input: @Composable () -> Unit,
) {
  val colors = AppTheme.colors

  Layout(
    content = {
      Box {
        if (label != null) {
          Text(
            text = label,
            style = AppType.body,
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(colors.tertiarySystemFill)
            .border(
              width = 1.5.dp,
              color = if (isFocused) colors.accent else Color.Transparent,
              shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        input()
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isFocused) {
          Icon(
            painter = painterResource(R.drawable.ic_check_circle_fill),
            contentDescription = stringResource(R.string.done),
            tint = colors.accent,
            modifier =
              Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).clickable {
                Haptics.impact(Haptics.ImpactStyle.Light)
                onCommit()
              },
          )
        }

        if (onDelete != null) {
          DeleteButton(onDelete = onDelete)
        }
      }
    },
    modifier = modifier.fillMaxWidth(),
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val minSpacing = 20.dp.roundToPx()
    val minInputWidth = (70.dp + 16.dp).roundToPx()

    val buttons = measurables[2].measure(loose)
    val available = width - buttons.width

    val label =
      measurables[0].measure(
        loose.copy(maxWidth = (available - minSpacing - minInputWidth).coerceAtLeast(0))
      )

    // The input shares the remaining space with the spacing before it
    val remaining = available - label.width
    val inputWidth =
      max(minInputWidth, remaining / 2).coerceAtMost(max(minInputWidth, remaining - minSpacing))
    val input =
      measurables[1].measure(loose.copy(minWidth = inputWidth, maxWidth = inputWidth))

    val height =
      max(max(label.height, input.height), buttons.height).coerceAtLeast(constraints.minHeight)
    layout(width, height) {
      label.placeRelative(0, (height - label.height) / 2)
      input.placeRelative(available - input.width, (height - input.height) / 2)
      buttons.placeRelative(width - buttons.width, (height - buttons.height) / 2)
    }
  }
}

/** The button that removes an array element. */
@Composable
fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier) {
  val colors = AppTheme.colors
  Icon(
    painter = painterResource(R.drawable.ic_delete),
    contentDescription = stringResource(R.string.delete),
    tint = colors.red.copy(alpha = 0.8f),
    modifier =
      modifier.padding(start = 10.dp).size(22.dp).clip(CircleShape).clickable {
        Haptics.impact(Haptics.ImpactStyle.Medium)
        onDelete()
      },
  )
}
