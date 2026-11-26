// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.littletonrobotics.advantagetune.R

/** The stack of destinations pushed on top of a tab's root view. */
class Navigator<T : Any> {
  class Entry<T>(val id: Long, val depth: Int, val destination: T)

  val backStack = mutableStateListOf<Entry<T>>()
  private var nextId = 0L

  fun push(destination: T) {
    backStack.add(Entry(nextId++, backStack.size + 1, destination))
  }

  fun pop() {
    if (backStack.isNotEmpty()) {
      backStack.removeAt(backStack.lastIndex)
    }
  }

  fun popToRoot() {
    backStack.clear()
  }
}

/** Shows the root view or the destination on top of the navigator's stack. */
@Composable
fun <T : Any> NavigationStack(
  navigator: Navigator<T>,
  modifier: Modifier = Modifier,
  root: @Composable () -> Unit,
  destination: @Composable (T) -> Unit,
) {
  val stateHolder = rememberSaveableStateHolder()
  val current = navigator.backStack.lastOrNull()

  BackHandler(enabled = current != null) { navigator.pop() }

  // Forget the state of destinations that were popped
  val knownIds = remember { mutableSetOf<Long>() }
  val currentIds = navigator.backStack.map { it.id }
  LaunchedEffect(currentIds) {
    val removed = knownIds - currentIds.toSet()
    removed.forEach { stateHolder.removeState(it) }
    knownIds.clear()
    knownIds.addAll(currentIds)
  }

  AnimatedContent(
    targetState = current,
    modifier = modifier.fillMaxSize(),
    contentKey = { it?.id ?: -1L },
    transitionSpec = {
      val isPush = (targetState?.depth ?: 0) > (initialState?.depth ?: 0)
      val direction = if (isPush) SlideDirection.Start else SlideDirection.End
      slideIntoContainer(direction, tween(300)) togetherWith
        slideOutOfContainer(direction, tween(300))
    },
    label = "NavigationStack",
  ) { entry ->
    stateHolder.SaveableStateProvider(entry?.id ?: -1L) {
      Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.systemBackground)) {
        if (entry == null) {
          root()
        } else {
          destination(entry.destination)
        }
      }
    }
  }
}

private val SafeAreaTop: WindowInsets
  @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

/** The navigation bar of a root view, with a large title below the bar's buttons. */
@Composable
fun LargeTitleBar(
  title: String,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(AppTheme.colors.systemBackground)
        .windowInsetsPadding(SafeAreaTop)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
      horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
      content = actions,
    )
    Text(
      text = title,
      style = AppType.largeTitle,
      color = AppTheme.colors.label,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
    )
  }
}

/** The navigation bar of a pushed view, with a back button and a centered title. */
@Composable
fun InlineTitleBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Layout(
    content = {
      BarButton(
        icon = R.drawable.ic_arrow_back,
        contentDescription = stringResource(R.string.back),
        onClick = onBack,
      )
      Text(
        text = title,
        style = AppType.headline,
        color = AppTheme.colors.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    },
    modifier =
      modifier
        .fillMaxWidth()
        .background(AppTheme.colors.systemGroupedBackground)
        .windowInsetsPadding(SafeAreaTop)
        .height(48.dp)
        .padding(horizontal = 4.dp),
  ) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val back = measurables[0].measure(loose)
    val trailing = measurables[2].measure(loose)

    // Keep the title centered unless the buttons leave no room for it
    val spacing = 8.dp.roundToPx()
    val sideWidth = max(back.width, trailing.width) + spacing
    val centeredWidth = constraints.maxWidth - 2 * sideWidth
    val titleMaxWidth =
      if (centeredWidth >= measurables[1].minIntrinsicWidth(constraints.maxHeight)) {
        centeredWidth
      } else {
        constraints.maxWidth - back.width - trailing.width - 2 * spacing
      }
    val titlePlaceable = measurables[1].measure(loose.copy(maxWidth = titleMaxWidth.coerceAtLeast(0)))

    val width = constraints.maxWidth
    val height = constraints.maxHeight
    layout(width, height) {
      back.placeRelative(0, (height - back.height) / 2)
      trailing.placeRelative(width - trailing.width, (height - trailing.height) / 2)

      val centeredX = (width - titlePlaceable.width) / 2
      val minX = back.width + spacing
      val maxX = width - trailing.width - spacing - titlePlaceable.width
      val titleX = centeredX.coerceIn(minX, max(minX, maxX))
      titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
    }
  }
}

/** An icon button for navigation bars. */
@Composable
fun BarButton(
  @androidx.annotation.DrawableRes icon: Int,
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
      painter = painterResource(icon),
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(24.dp),
    )
  }
}
