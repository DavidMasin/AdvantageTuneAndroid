// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R

@Composable
private fun badgeColor(typeName: String): Color {
  val colors = AppTheme.colors
  val lower = typeName.lowercase()
  return if (lower.startsWith("bool")) {
    colors.green
  } else if (lower.startsWith("double") || lower.startsWith("float")) {
    colors.blue
  } else if (lower.startsWith("int")) {
    colors.indigo
  } else if (lower.startsWith("string") || lower.startsWith("json")) {
    colors.orange
  } else if (lower.contains("struct:") || lower.contains("proto:")) {
    colors.purple
  } else if (lower.contains("[]")) {
    colors.teal
  } else {
    colors.gray
  }
}

@DrawableRes
private fun iconName(typeName: String): Int {
  val lower = typeName.lowercase()
  return if (lower == "boolean") {
    R.drawable.ic_toggle_on
  } else if (lower == "boolean[]") {
    R.drawable.ic_checklist
  } else if (lower == "double" || lower == "float") {
    R.drawable.ic_show_chart
  } else if (lower == "double[]" || lower == "float[]") {
    R.drawable.ic_bar_chart
  } else if (lower == "int") {
    R.drawable.ic_tag
  } else if (lower == "int[]") {
    R.drawable.ic_tag_square
  } else if (lower == "string" || lower == "json") {
    R.drawable.ic_format_quote
  } else if (lower == "string[]") {
    R.drawable.ic_format_list_bulleted
  } else if (lower.startsWith("struct:")) {
    R.drawable.ic_deployed_code
  } else if (lower.startsWith("proto:")) {
    R.drawable.ic_package_2
  } else {
    R.drawable.ic_memory
  }
}

fun shortTypeLabel(typeName: String): String {
  if (typeName.startsWith("struct:")) {
    return typeName.substring(7)
  } else if (typeName.startsWith("proto:")) {
    return typeName.substring(6)
  }
  return typeName
}

@Composable
fun TypeBadgeView(typeName: String, modifier: Modifier = Modifier, compact: Boolean = false) {
  val badgeColor = badgeColor(typeName)

  if (compact) {
    Box(
      modifier =
        modifier
          .size(22.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(badgeColor.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(iconName(typeName)),
        contentDescription = null,
        tint = badgeColor,
        modifier = Modifier.size(15.dp),
      )
    }
  } else {
    Row(
      modifier =
        modifier
          .clip(CircleShape)
          .background(badgeColor.copy(alpha = 0.15f))
          .padding(horizontal = 6.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(iconName(typeName)),
        contentDescription = null,
        tint = badgeColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = shortTypeLabel(typeName),
        style = AppType.caption2.weight(FontWeight.Medium),
        color = badgeColor,
      )
    }
  }
}
