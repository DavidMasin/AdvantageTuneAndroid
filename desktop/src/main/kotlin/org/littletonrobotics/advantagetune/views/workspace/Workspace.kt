// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.littletonrobotics.advantagetune.models.UserDefaults

/**
 * The topics that are open next to the lists in a wide window. A topic that is opened takes the
 * place of the ones that are not pinned. Pinned topics stay open, also the next time the app
 * starts, so several values can be edited side by side.
 */
class Workspace {
  private val open = mutableStateListOf<String>()
  private val pinned = mutableStateListOf<String>()

  /** The topic to bring into view, with a count that changes whenever a topic is opened. */
  var revealed: Pair<String, Int>? by mutableStateOf(null)
    private set

  init {
    val stored = UserDefaults.string(pinnedKey)
    if (stored != null) {
      try {
        val names = JSONArray(stored)
        for (index in 0 until names.length()) {
          pinned.add(names.getString(index))
        }
        open.addAll(pinned)
      } catch (e: JSONException) {
        // Start without pinned topics if they cannot be read
      }
    }
  }

  /** Names of the open topics, in the order they are shown. */
  val topics: List<String>
    get() = open.toList()

  fun isPinned(name: String): Boolean = name in pinned

  /** Shows a topic. */
  fun open(name: String) {
    if (name !in open) {
      open.removeAll { it !in pinned }
      open.add(name)
    }
    revealed = Pair(name, (revealed?.second ?: 0) + 1)
  }

  fun close(name: String) {
    open.remove(name)
    if (pinned.remove(name)) {
      save()
    }
  }

  /** Pins an open topic, or lets the next topic that is opened take its place again. */
  fun setPinned(name: String, isPinned: Boolean) {
    if (name !in open || isPinned == (name in pinned)) return
    if (isPinned) {
      pinned.add(name)
    } else {
      pinned.remove(name)
    }
    save()
  }

  private fun save() {
    val names = open.filter { it in pinned }
    UserDefaults.set(JSONArray(names).toString(), pinnedKey)
  }

  private companion object {
    const val pinnedKey = "workspace_pinned_topics"
  }
}
