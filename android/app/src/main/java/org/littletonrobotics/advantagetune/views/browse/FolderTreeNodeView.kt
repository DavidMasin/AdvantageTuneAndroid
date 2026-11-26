// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.DisclosureIndicator
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.RowPosition
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.weight

/**
 * The row of a table that can be expanded in place to show its contents. The contents are shown
 * as the rows that follow it in the list.
 */
@Composable
fun FolderTreeNodeView(
  store: NTStore,
  node: TopicNode,
  position: RowPosition,
  isExpanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  indent: Dp = 0.dp,
) {
  val colors = AppTheme.colors
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val topicNames = node.children.filter { !it.isFolder }.mapNotNull { it.topic?.name }

  val currentIsExpanded by rememberUpdatedState(isExpanded)
  val currentTopicNames by rememberUpdatedState(topicNames)
  DisposableEffect(node.path) {
    onDispose {
      if (currentIsExpanded) {
        store.releaseSubscriptions(currentTopicNames)
      }
    }
  }

  ListRow(
    position = position,
    modifier = modifier,
    indent = indent,
    separatorInset = 16.dp + 22.dp + 10.dp,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_folder_fill),
        contentDescription = null,
        tint = colors.accent,
        modifier = Modifier.size(22.dp),
      )

      Text(
        text = node.name,
        style = AppType.body.weight(FontWeight.Medium),
        color = colors.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = AppType.leadingAlignment,
        modifier = Modifier.weight(1f),
      )

      TextBadge(
        text = Localized.count(node.children.size),
        style = AppType.caption2.weight(FontWeight.Bold),
        color = colors.secondaryLabel,
        backgroundColor = colors.systemGray5,
        shape = CircleShape,
      )

      DisclosureIndicator()

      // Expands the table in place
      Box(
        modifier =
          Modifier.size(32.dp)
            .clip(CircleShape)
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(),
              onClickLabel = stringResource(if (isExpanded) R.string.collapse else R.string.expand),
            ) {
              val expanded = !isExpanded
              if (expanded) {
                store.acquireSubscriptions(topicNames)
              } else {
                store.releaseSubscriptions(topicNames)
              }
              onExpandedChange(expanded)
            },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_chevron_right),
          contentDescription = null,
          tint = colors.accent,
          modifier =
            Modifier.size(22.dp).rotate(if (!isExpanded) 0f else if (isRtl) -90f else 90f),
        )
      }
    }
  }
}
