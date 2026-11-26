// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.components.ConnectionBar
import org.littletonrobotics.advantagetune.views.components.ContentUnavailableView
import org.littletonrobotics.advantagetune.views.components.LargeTitleBar
import org.littletonrobotics.advantagetune.views.components.NavigationStack
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.detail.TopicDetailView

@Composable
fun BrowseView(store: NTStore, navigator: Navigator<TopicNode>) {
  NavigationStack(
    navigator = navigator,
    root = {
      Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleBar(title = stringResource(R.string.browse))

        // Connection bar under navigation title
        ConnectionBar(store = store)

        Separator()

        if (!store.connectionStatus.isConnected && store.topicTree.isEmpty()) {
          ContentUnavailableView(
            title = stringResource(R.string.not_connected),
            icon = R.drawable.ic_public_off,
            description = stringResource(R.string.connect_to_browse),
          )
        } else {
          FolderListView(store = store, node = null, navigator = navigator)
        }
      }
    },
  ) { node ->
    when (node) {
      is TopicNode.Folder -> FolderListView(store = store, node = node, navigator = navigator)
      is TopicNode.Topic ->
        TopicDetailView(store = store, topic = node.nt4Topic, onBack = { navigator.pop() })
    }
  }
}
