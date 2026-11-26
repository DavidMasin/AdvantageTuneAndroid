// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.store.NTStore

/** A list row showing a topic and its live value. */
@Composable
fun TopicRowView(
  store: NTStore,
  topic: NT4Topic,
  position: RowPosition,
  modifier: Modifier = Modifier,
  title: String? = null,
  isPublished: Boolean = false,
  indent: Dp = 0.dp,
  onClick: (() -> Unit)? = null,
) {
  val colors = AppTheme.colors
  val displayTitle = title ?: topic.name
  val liveValue = store.latestValues[topic.name]

  DisposableEffect(topic.name) {
    store.acquireSubscription(topic.name)
    onDispose { store.releaseSubscription(topic.name) }
  }

  ListRow(
    position = position,
    modifier = modifier,
    indent = indent,
    separatorInset = 16.dp + 22.dp + 10.dp,
    showsChevron = onClick != null,
    onClick = onClick,
    contextMenu = { expanded, onDismiss, offset ->
      TopicContextMenu(expanded, onDismiss, offset, store = store, topic = topic, value = liveValue)
    },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TypeBadgeView(typeName = topic.type, compact = true)

      LeadingTrailingRow(
        modifier = Modifier.weight(1f),
        leading = {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = displayTitle,
                style = AppType.body.weight(FontWeight.Medium),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f, fill = false),
              )

              if (isPublished) {
                TextBadge(
                  text = stringResource(R.string.pub),
                  style = AppType.system(size = 9, weight = FontWeight.Bold),
                  color = colors.orange,
                  backgroundColor = colors.orange.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(3.dp),
                  horizontalPadding = 4.dp,
                  verticalPadding = 1.dp,
                )
              }
            }

            Text(
              text = shortTypeLabel(topic.type),
              style = AppType.caption,
              color = colors.secondaryLabel,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        trailing = {
          if (liveValue != null) {
            Text(
              text = liveValue.formattedString,
              style = AppType.subheadline.monospaced().weight(FontWeight.SemiBold),
              color = if (store.connectionStatus.isConnected) colors.label else colors.secondaryLabel,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = AppType.trailingAlignment,
            )
          } else {
            Text(text = "—", style = AppType.subheadline, color = colors.secondaryLabel)
          }
        },
      )
    }
  }
}
