// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.MainViewModel
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.ContentUnavailableView
import org.littletonrobotics.advantagetune.views.components.DesktopIcons
import org.littletonrobotics.advantagetune.views.components.IconBarButton
import org.littletonrobotics.advantagetune.views.components.InlineTitleBar
import org.littletonrobotics.advantagetune.views.components.LocalTitleBarActions
import org.littletonrobotics.advantagetune.views.components.LocalTitleBarLeading
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.dragToScroll
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.detail.TopicDetailView

// A topic is as wide as on a phone, or wider if there is room
private val MinTopicWidth = 360.dp
private val MaxTopicWidth = 560.dp

/** Shows the open topics side by side. */
@Composable
fun WorkspaceView(store: NTStore, workspace: Workspace, modifier: Modifier = Modifier) {
  val colors = AppTheme.colors
  val names = workspace.topics

  BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.systemGroupedBackground)) {
    if (names.isEmpty()) {
      ContentUnavailableView(
        title = "No Topic Selected",
        icon = R.drawable.ic_tune,
        description = "Select a topic to view and edit it here.",
      )
      return@BoxWithConstraints
    }

    val topicWidth = (maxWidth / names.size).coerceIn(MinTopicWidth, MaxTopicWidth)
    val remainingWidth = maxWidth - topicWidth * names.size
    val scrollState = rememberScrollState()

    // Bring the topic that was opened into view
    val density = LocalDensity.current
    val revealed = workspace.revealed
    LaunchedEffect(revealed) {
      val index = names.indexOf(revealed?.first)
      if (index < 0) return@LaunchedEffect
      // Wait for the topic to be laid out
      withFrameNanos {}
      val start = with(density) { (topicWidth * index).roundToPx() }
      val end = with(density) { (topicWidth * (index + 1)).roundToPx() }
      if (start < scrollState.value) {
        scrollState.animateScrollTo(start)
      } else if (end > scrollState.value + scrollState.viewportSize) {
        scrollState.animateScrollTo(end - scrollState.viewportSize)
      }
    }

    // Dragging only moves the topics when they do not fit, so it is left to their content otherwise
    val dragModifier =
      if (scrollState.maxValue > 0) {
        Modifier.dragToScroll(scrollState, Orientation.Horizontal)
      } else {
        Modifier
      }

    Row(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState).then(dragModifier)) {
      for (name in names) {
        key(name) {
          WorkspaceTopic(
            store = store,
            workspace = workspace,
            name = name,
            modifier = Modifier.width(topicWidth).fillMaxHeight(),
          )
          Box(modifier = Modifier.fillMaxHeight().width(0.5.dp).background(colors.separator))
        }
      }

      // Until a topic is pinned, the room next to the open topic says what pinning is for
      if (names.size == 1 && !workspace.isPinned(names[0]) && remainingWidth >= MinTopicWidth) {
        PinHint(modifier = Modifier.width(remainingWidth).fillMaxHeight())
      }
    }
  }
}

@Composable
private fun PinHint(modifier: Modifier = Modifier) {
  val colors = AppTheme.colors
  Column(
    modifier = modifier.padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = DesktopIcons.Pin,
      contentDescription = null,
      tint = colors.tertiaryLabel,
      modifier = Modifier.size(40.dp),
    )
    Text(
      text = "Pin a topic to keep it open, then select another to edit them side by side.",
      style = AppType.subheadline,
      color = colors.secondaryLabel,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 12.dp).widthIn(max = 280.dp),
    )
  }
}

/** One open topic, with buttons to pin and to close it. */
@Composable
private fun WorkspaceTopic(
  store: NTStore,
  workspace: Workspace,
  name: String,
  modifier: Modifier = Modifier,
) {
  val colors = AppTheme.colors
  val topic = store.topicsByName[name]
  val isPinned = workspace.isPinned(name)

  CompositionLocalProvider(
    LocalTitleBarLeading provides
      { onBack ->
        IconBarButton(icon = DesktopIcons.Close, contentDescription = "Close", onClick = onBack)
      },
    LocalTitleBarActions provides
      {
        IconBarButton(
          icon = if (isPinned) DesktopIcons.PinFilled else DesktopIcons.Pin,
          contentDescription = if (isPinned) "Unpin" else "Pin",
          tint = if (isPinned) colors.accent else colors.gray,
          onClick = { workspace.setPinned(name, !isPinned) },
        )
      },
  ) {
    Box(modifier = modifier) {
      if (topic != null) {
        TopicDetailView(store = store, topic = topic, onBack = { workspace.close(name) })
      } else {
        // A pinned topic that the server has not announced, as before the app is connected
        Column(modifier = Modifier.fillMaxSize()) {
          InlineTitleBar(
            title = name.split("/").lastOrNull { it.isNotEmpty() } ?: name,
            onBack = { workspace.close(name) },
          )
          ContentUnavailableView(
            title = name,
            icon = R.drawable.ic_public_off,
            description = stringResource(R.string.not_connected),
          )
        }
      }
    }
  }
}

/**
 * Opens topics in the workspace instead of on top of the lists, for as long as the workspace is
 * shown.
 */
@Composable
fun WorkspaceNavigation(viewModel: MainViewModel, workspace: Workspace, isEnabled: Boolean) {
  DisposableEffect(viewModel, workspace, isEnabled) {
    if (isEnabled) {
      // Topics that are open on top of the lists move to the workspace
      val open =
        listOfNotNull(
          viewModel.browseNavigator.popTopics { (it as? TopicNode.Topic)?.nt4Topic },
          viewModel.valuesNavigator.popTopics { it },
          viewModel.searchNavigator.popTopics { it },
        )
      open.firstOrNull()?.let { workspace.open(it.name) }

      viewModel.browseNavigator.interceptor = { node ->
        if (node is TopicNode.Topic) {
          workspace.open(node.nt4Topic.name)
          true
        } else {
          false
        }
      }
      val openTopic = { topic: NT4Topic ->
        workspace.open(topic.name)
        true
      }
      viewModel.valuesNavigator.interceptor = openTopic
      viewModel.searchNavigator.interceptor = openTopic
    }

    onDispose {
      viewModel.browseNavigator.interceptor = null
      viewModel.valuesNavigator.interceptor = null
      viewModel.searchNavigator.interceptor = null
    }
  }
}

/** Removes the topics at the top of the stack and returns the last of them. */
private fun <T : Any> Navigator<T>.popTopics(topic: (T) -> NT4Topic?): NT4Topic? {
  var last: NT4Topic? = null
  while (true) {
    val top = backStack.lastOrNull() ?: break
    val found = topic(top.destination) ?: break
    last = last ?: found
    pop()
  }
  return last
}
