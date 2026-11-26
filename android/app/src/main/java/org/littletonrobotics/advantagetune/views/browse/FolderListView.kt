// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.ConnectionStatusPill
import org.littletonrobotics.advantagetune.views.components.ContentUnavailableView
import org.littletonrobotics.advantagetune.views.components.InlineTitleBar
import org.littletonrobotics.advantagetune.views.components.InsetGroupedList
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.TopicRowView
import org.littletonrobotics.advantagetune.views.components.section

/** A row of the flattened tree of tables shown by [FolderListView]. */
private sealed class TreeRow {
  abstract val depth: Int

  class Folder(val node: TopicNode, override val depth: Int, val isExpanded: Boolean) : TreeRow()

  class Topic(val node: TopicNode, override val depth: Int) : TreeRow()
}

/** Lists the tables followed by the contents of the ones that are expanded. */
private fun flattenTree(
  subfolders: List<TopicNode>,
  expandedPaths: List<String>,
  depth: Int = 0,
  rows: MutableList<TreeRow> = mutableListOf(),
): List<TreeRow> {
  for (folder in subfolders) {
    val isExpanded = expandedPaths.contains(folder.path)
    rows.add(TreeRow.Folder(folder, depth, isExpanded))

    if (isExpanded) {
      // Keys
      for (child in folder.children) {
        if (!child.isFolder) {
          rows.add(TreeRow.Topic(child, depth + 1))
        }
      }

      // Subfolders
      flattenTree(folder.children.filter { it.isFolder }, expandedPaths, depth + 1, rows)
    }
  }
  return rows
}

@Composable
fun FolderListView(store: NTStore, node: TopicNode?, navigator: Navigator<TopicNode>) {
  val isRoot = node == null
  val folderPath = node?.path ?: "/"

  val currentChildren: List<TopicNode> =
    if (node != null) {
      (store.node(node.path) ?: node).children
    } else {
      store.topicTree
    }

  val subfolders = currentChildren.filter { it.isFolder }
  val topics = currentChildren.filter { !it.isFolder }

  // Paths of the tables that are expanded
  val expandedPaths: SnapshotStateList<String> =
    rememberSaveable(
      saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) {
      mutableListOf<String>().toMutableStateList()
    }

  val keysHeader = stringResource(R.string.keys_count, topics.size)
  val tablesHeader = stringResource(R.string.tables_count, subfolders.size)

  Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.systemGroupedBackground)) {
    if (!isRoot) {
      InlineTitleBar(title = node.name, onBack = { navigator.pop() }) {
        if (!store.connectionStatus.isConnected) {
          ConnectionStatusPill(store = store)
        }
      }
    }

    if (subfolders.isEmpty() && topics.isEmpty()) {
      if (isRoot) {
        ContentUnavailableView(
          title = stringResource(R.string.no_topics_announced),
          icon = R.drawable.ic_inbox,
          description = stringResource(R.string.waiting_for_topics),
        )
      } else {
        ContentUnavailableView(
          title = stringResource(R.string.empty_table),
          icon = R.drawable.ic_folder,
          description = stringResource(R.string.no_subtables_or_keys, folderPath),
        )
      }
    } else {
      InsetGroupedList {
        // Keys
        if (topics.isNotEmpty()) {
          section(key = "keys", header = keysHeader) {
            for (child in topics) {
              val topic = child.topic ?: continue
              row(key = child.id, contentType = "topic") { position ->
                TopicRowView(
                  store = store,
                  topic = topic,
                  position = position,
                  title = child.name,
                  isPublished = store.isPublished(topic.name),
                  onClick = { navigator.push(child) },
                )
              }
            }
          }
        }

        // Subtables
        if (subfolders.isNotEmpty()) {
          section(key = "tables", header = tablesHeader) {
            for (treeRow in flattenTree(subfolders, expandedPaths)) {
              when (treeRow) {
                is TreeRow.Folder ->
                  row(key = "folder:${treeRow.node.id}", contentType = "folder") { position ->
                    FolderTreeNodeView(
                      store = store,
                      node = treeRow.node,
                      position = position,
                      indent = (treeRow.depth * 20).dp,
                      isExpanded = treeRow.isExpanded,
                      onExpandedChange = { expanded ->
                        if (expanded) {
                          expandedPaths.add(treeRow.node.path)
                        } else {
                          expandedPaths.remove(treeRow.node.path)
                        }
                      },
                      onClick = { navigator.push(treeRow.node) },
                    )
                  }

                is TreeRow.Topic -> {
                  val topic = treeRow.node.topic ?: continue
                  row(key = "topic:${treeRow.node.id}", contentType = "topic") { position ->
                    TopicRowView(
                      store = store,
                      topic = topic,
                      position = position,
                      title = treeRow.node.name,
                      isPublished = store.isPublished(topic.name),
                      indent = (treeRow.depth * 20).dp,
                      onClick = { navigator.push(treeRow.node) },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
