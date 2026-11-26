// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.tuning

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.localizedStandardCompare
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.store.TunedEntry
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.BarButton
import org.littletonrobotics.advantagetune.views.components.ConnectionBar
import org.littletonrobotics.advantagetune.views.components.ContentUnavailableView
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.InsetGroupedList
import org.littletonrobotics.advantagetune.views.components.LargeTitleBar
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.LocalOpenTopics
import org.littletonrobotics.advantagetune.views.components.Menu
import org.littletonrobotics.advantagetune.views.components.MenuItem
import org.littletonrobotics.advantagetune.views.components.NavigationStack
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.RowPosition
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.TopicContextMenu
import org.littletonrobotics.advantagetune.views.components.monospaced
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.rememberShareAction
import org.littletonrobotics.advantagetune.views.components.rememberTimeFormatter
import org.littletonrobotics.advantagetune.views.components.section
import org.littletonrobotics.advantagetune.views.components.shape
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.swipeGesture
import org.littletonrobotics.advantagetune.views.components.weight
import org.littletonrobotics.advantagetune.views.detail.TopicDetailView

@Composable
fun TuningView(store: NTStore, navigator: Navigator<NT4Topic>) {
  val share = rememberShareAction()

  NavigationStack(
    navigator = navigator,
    root = {
      val entries =
        store.tunedEntries.values.sortedWith { a, b ->
          localizedStandardCompare(a.topicPath, b.topicPath)
        }
      val tunedValuesHeader = stringResource(R.string.tuned_values_count, entries.size)

      Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleBar(title = stringResource(R.string.tuning)) {
          if (entries.isNotEmpty()) {
            BarButton(
              icon = R.drawable.ic_share,
              contentDescription = stringResource(R.string.share),
              onClick = { share(formattedShareText(entries)) },
            )

            Box {
              var menuExpanded by remember { mutableStateOf(false) }
              BarButton(
                icon = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.delete),
                tint = AppTheme.colors.red,
                onClick = { menuExpanded = true },
              )

              Menu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                MenuItem(
                  title = stringResource(R.string.unpublish_all),
                  icon = R.drawable.ic_dangerous,
                ) {
                  menuExpanded = false
                  Haptics.impact(Haptics.ImpactStyle.Medium)
                  store.unpublishAllTunedTopics()
                }

                MenuItem(
                  title = stringResource(R.string.revert_all),
                  icon = R.drawable.ic_undo,
                  isDestructive = true,
                ) {
                  menuExpanded = false
                  Haptics.notification(Haptics.NotificationType.Warning)
                  store.revertAllTunedTopics()
                }
              }
            }
          }
        }

        // Connection bar
        ConnectionBar(store = store)

        Separator()

        if (entries.isEmpty()) {
          ContentUnavailableView(
            title = stringResource(R.string.no_tuned_values_title),
            icon = R.drawable.ic_tune,
            description = stringResource(R.string.modified_values_appear_here),
          )
        } else {
          InsetGroupedList {
            section(key = "tuned", header = tunedValuesHeader) {
              for (entry in entries) {
                row(key = entry.id, contentType = "entry") { position ->
                  val topic =
                    store.topicsByName[entry.topicPath]
                      ?: NT4Topic(id = -1, name = entry.topicPath, type = entry.topicType)
                  TunedEntryRow(
                    store = store,
                    entry = entry,
                    topic = topic,
                    position = position,
                    onClick = { navigator.push(topic) },
                  )
                }
              }
            }
          }
        }
      }
    },
  ) { topic ->
    TopicDetailView(store = store, topic = topic, onBack = { navigator.pop() })
  }
}

private fun formattedShareText(entries: List<TunedEntry>): String {
  if (entries.isEmpty()) {
    return Localized.noTunedValues()
  }

  val lines = mutableListOf<String>()
  for (entry in entries) {
    lines.add("${entry.topicPath} = ${entry.currentValue.fullString}")
  }

  return lines.joinToString("\n")
}

@Composable
private fun TunedEntryRow(
  store: NTStore,
  entry: TunedEntry,
  topic: NT4Topic,
  position: RowPosition,
  onClick: () -> Unit,
) {
  val colors = AppTheme.colors
  val formatTime = rememberTimeFormatter()
  val displayValue =
    if (store.connectionStatus.isConnected) {
      store.latestValues[entry.topicPath] ?: entry.currentValue
    } else {
      entry.currentValue
    }

  DisposableEffect(entry.topicPath) {
    store.acquireSubscription(entry.topicPath)
    onDispose { store.releaseSubscription(entry.topicPath) }
  }

  SwipeActionsRow(
    position = position,
    actions =
      listOf(
        SwipeAction(
          title = stringResource(R.string.unpublish),
          icon = R.drawable.ic_dangerous,
          color = colors.gray,
        ) {
          Haptics.impact(Haptics.ImpactStyle.Medium)
          store.unpublishTopic(entry.topicPath)
        },
        // The last action also runs when the row is swiped all the way
        SwipeAction(
          title = stringResource(R.string.revert),
          icon = R.drawable.ic_undo,
          color = colors.red,
        ) {
          Haptics.notification(Haptics.NotificationType.Warning)
          store.revertAndUnpublishTopic(entry.topicPath)
        },
      )
  ) {
    ListRow(
      position = position,
      showsChevron = true,
      isSelected = topic.name in LocalOpenTopics.current,
      onClick = onClick,
      contextMenu = { expanded, onDismiss, offset ->
        TopicContextMenu(expanded, onDismiss, offset, store = store, topic = topic, value = displayValue)
      },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = entry.topicPath,
            style = AppType.subheadline.weight(FontWeight.SemiBold),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = AppType.leadingAlignment,
            modifier = Modifier.weight(1f),
          )

          TextBadge(
            text = stringResource(R.string.published),
            style = AppType.system(size = 9, weight = FontWeight.Bold),
            color = colors.orange,
            backgroundColor = colors.orange.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp),
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = displayValue.formattedString,
            style = AppType.subheadline.monospaced().weight(FontWeight.Bold),
            color = colors.accent,
            textAlign = AppType.leadingAlignment,
            modifier = Modifier.weight(1f),
          )

          Text(
            text = formatTime(entry.lastModified),
            style = AppType.caption2,
            color = colors.secondaryLabel,
            maxLines = 1,
          )
        }
      }
    }
  }
}

// MARK: - Swipe Actions

private class SwipeAction(
  val title: String,
  @DrawableRes val icon: Int,
  val color: Color,
  val action: () -> Unit,
)

private val SwipeActionWidth: Dp = 76.dp

/**
 * Reveals actions when the content is swiped towards the leading edge. Swiping all the way runs
 * the last action.
 */
@Composable
private fun SwipeActionsRow(
  position: RowPosition,
  actions: List<SwipeAction>,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

  // Distance the content has been pulled towards the leading edge
  val pull = remember { Animatable(0f) }
  var rowWidth by remember { mutableIntStateOf(0) }
  val actionsWidth = with(density) { (SwipeActionWidth * actions.size).toPx() }
  val fullSwipeThreshold = rowWidth * 0.6f

  fun close() {
    scope.launch { pull.animateTo(0f) }
  }

  Box(modifier = modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width }) {
    if (pull.value > 0f) {
      val isFullSwipe = pull.value > fullSwipeThreshold && fullSwipeThreshold > actionsWidth
      Row(
        modifier =
          Modifier.matchParentSize()
            .padding(horizontal = 16.dp)
            .clip(position.shape())
            .background(actions.last().color),
        horizontalArrangement = Arrangement.End,
      ) {
        val visibleActions = if (isFullSwipe) listOf(actions.last()) else actions
        val width = with(density) { (pull.value / visibleActions.size).toDp() }
        for (action in visibleActions) {
          Column(
            modifier =
              Modifier.width(width.coerceAtLeast(0.dp))
                .fillMaxHeight()
                .background(action.color)
                .clickable {
                  close()
                  action.action()
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              painter = painterResource(action.icon),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = action.title,
              style = AppType.caption2.weight(FontWeight.Medium),
              color = Color.White,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Clip,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }

    Box(
      modifier =
        Modifier.offset { IntOffset(-pull.value.roundToInt(), 0) }
          .swipeGesture(
            onDelta = { delta ->
              val towardsLeading = if (isRtl) delta else -delta
              scope.launch {
                pull.snapTo((pull.value + towardsLeading).coerceIn(0f, rowWidth.toFloat()))
              }
            },
            onStopped = { velocity ->
              val towardsLeading = if (isRtl) velocity else -velocity
              if (pull.value > fullSwipeThreshold && fullSwipeThreshold > actionsWidth) {
                pull.animateTo(rowWidth.toFloat())
                actions.last().action()
                pull.snapTo(0f)
              } else if (pull.value > actionsWidth / 2 || towardsLeading > 800f) {
                pull.animateTo(if (abs(towardsLeading) > 800f && towardsLeading < 0) 0f else actionsWidth)
              } else {
                pull.animateTo(0f)
              }
            },
          )
    ) {
      content()

      // While the actions are showing, a tap on the row hides them again
      if (pull.value > 0f) {
        Box(
          modifier =
            Modifier.matchParentSize().clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) {
              close()
            }
        )
      }
    }
  }
}
