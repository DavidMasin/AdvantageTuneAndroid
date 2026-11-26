// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import okio.ByteString
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.models.hexString
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.RowPosition
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.TopicContextMenu
import org.littletonrobotics.advantagetune.views.components.monospaced
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.weight

/** A list row showing the bytes of a raw topic. */
@Composable
fun RawTelemetryView(
  topic: NT4Topic,
  rawData: ByteString?,
  position: RowPosition,
  modifier: Modifier = Modifier,
  fallbackValue: TopicValue? = null,
  isConnected: Boolean = true,
) {
  val colors = AppTheme.colors
  val rawTelemetryLabel =
    if (topic.type.startsWith("proto:") || topic.isProtobuf) {
      stringResource(R.string.protobuf_value)
    } else if (topic.type.startsWith("struct:") || topic.isStruct) {
      stringResource(R.string.struct_value)
    } else {
      stringResource(R.string.server_value)
    }

  ListRow(
    position = position,
    modifier = modifier,
    contextMenu = { expanded, onDismiss, offset ->
      TopicContextMenu(
        expanded,
        onDismiss,
        offset,
        topicName = topic.name,
        value = fallbackValue,
        rawData = rawData,
      )
    },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_memory),
            contentDescription = null,
            tint = colors.secondaryLabel,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = rawTelemetryLabel,
            style = AppType.subheadline.weight(FontWeight.Medium),
            color = colors.secondaryLabel,
          )
        }

        if (rawData != null) {
          TextBadge(
            text = stringResource(R.string.bytes_count, rawData.size),
            style = AppType.caption.monospaced().weight(FontWeight.Bold),
            color = if (isConnected) colors.label else colors.secondaryLabel,
            backgroundColor = colors.tertiarySystemFill,
            shape = CircleShape,
          )
        } else {
          Text(
            text = stringResource(R.string.waiting_for_value),
            style = AppType.body,
            fontStyle = FontStyle.Italic,
            color = colors.secondaryLabel,
          )
        }
      }

      if (rawData != null && rawData.size > 0) {
        val hexSnippet = rawData.hexString(512)
        Text(
          text = stringResource(R.string.hex_value, hexSnippet, if (rawData.size > 512) " …" else ""),
          style = AppType.caption2.monospaced(),
          color = colors.secondaryLabel,
        )
      }
    }
  }
}
