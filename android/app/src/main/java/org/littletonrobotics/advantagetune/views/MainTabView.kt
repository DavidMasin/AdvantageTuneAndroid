// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.browse.BrowseView
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.search.SearchView
import org.littletonrobotics.advantagetune.views.tuning.TuningView

enum class AppTab(@StringRes val title: Int, @DrawableRes val icon: Int) {
  Browse(R.string.browse, R.drawable.ic_folder),
  Values(R.string.tuning, R.drawable.ic_tune),
  Search(R.string.search, R.drawable.ic_search),
}

/** Holds the state that outlives the activity, like the views' state on iOS. */
class MainViewModel : ViewModel() {
  val store = NTStore()
  val browseNavigator = Navigator<TopicNode>()
  val valuesNavigator = Navigator<NT4Topic>()
  val searchNavigator = Navigator<NT4Topic>()

  init {
    store.connect()
  }

  override fun onCleared() {
    store.disconnect()
  }
}

@Composable
fun MainTabView(viewModel: MainViewModel = viewModel()) {
  val store = viewModel.store
  val colors = AppTheme.colors
  var selectedTab by rememberSaveable { mutableStateOf(AppTab.Browse) }
  var searchActivation by rememberSaveable { mutableIntStateOf(0) }
  val stateHolder = rememberSaveableStateHolder()

  fun select(tab: AppTab) {
    if (selectedTab == tab) {
      // Selecting the current tab returns to its root view
      when (tab) {
        AppTab.Browse -> viewModel.browseNavigator.popToRoot()
        AppTab.Values -> viewModel.valuesNavigator.popToRoot()
        AppTab.Search -> viewModel.searchNavigator.popToRoot()
      }
    } else {
      selectedTab = tab
    }
    if (tab == AppTab.Search) {
      searchActivation += 1
    }
  }

  LaunchedEffect(store) {
    snapshotFlow { store.connectionStatus.isConnected }
      .drop(1)
      .collect { isConnected ->
        if (isConnected) {
          Haptics.notification(Haptics.NotificationType.Success)
        } else {
          Haptics.impact(Haptics.ImpactStyle.Light)
        }
      }
  }

  val tabContent: @Composable () -> Unit = {
    stateHolder.SaveableStateProvider(selectedTab) {
      when (selectedTab) {
        AppTab.Browse -> BrowseView(store = store, navigator = viewModel.browseNavigator)
        AppTab.Values -> TuningView(store = store, navigator = viewModel.valuesNavigator)
        AppTab.Search ->
          SearchView(
            store = store,
            navigator = viewModel.searchNavigator,
            activation = searchActivation,
          )
      }
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.systemBackground)) {
    val tunedCount = store.tunedEntries.size

    if (maxWidth >= 600.dp) {
      // Wide windows show the tabs at the side
      Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(containerColor = colors.barBackground) {
          for (tab in AppTab.entries) {
            NavigationRailItem(
              selected = selectedTab == tab,
              onClick = { select(tab) },
              icon = { TabIcon(tab = tab, badgeCount = tunedCount) },
              label = { Text(text = stringResource(tab.title)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = colors.accent,
                  selectedTextColor = colors.accent,
                  indicatorColor = colors.accent.copy(alpha = 0.15f),
                  unselectedIconColor = colors.gray,
                  unselectedTextColor = colors.gray,
                ),
            )
          }
        }

        Box(modifier = Modifier.fillMaxHeight().width(0.5.dp).background(colors.separator))

        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
              .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
              )
        ) {
          tabContent()
        }
      }
    } else {
      val density = LocalDensity.current
      var barHeight by remember { mutableIntStateOf(0) }

      Column(modifier = Modifier.fillMaxSize()) {
        // The keyboard covers the tab bar, so only the part above the bar pushes the content up
        Box(
          modifier =
            Modifier.weight(1f)
              .consumeWindowInsets(PaddingValues(bottom = with(density) { barHeight.toDp() }))
              .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                  WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
              )
        ) {
          tabContent()
        }

        Separator()

        NavigationBar(
          modifier = Modifier.onSizeChanged { barHeight = it.height },
          containerColor = colors.barBackground,
        ) {
          for (tab in AppTab.entries) {
            NavigationBarItem(
              selected = selectedTab == tab,
              onClick = { select(tab) },
              icon = { TabIcon(tab = tab, badgeCount = tunedCount) },
              label = { Text(text = stringResource(tab.title)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = colors.accent,
                  selectedTextColor = colors.accent,
                  indicatorColor = colors.accent.copy(alpha = 0.15f),
                  unselectedIconColor = colors.gray,
                  unselectedTextColor = colors.gray,
                ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIcon(tab: AppTab, badgeCount: Int) {
  val icon: @Composable () -> Unit = {
    Icon(
      painter = painterResource(tab.icon),
      contentDescription = null,
      modifier = Modifier.size(24.dp),
    )
  }

  if (tab == AppTab.Values && badgeCount > 0) {
    BadgedBox(
      badge = {
        Badge(containerColor = AppTheme.colors.red, contentColor = Color.White) {
          Text(text = Localized.count(badgeCount))
        }
      }
    ) {
      icon()
    }
  } else {
    icon()
  }
}
