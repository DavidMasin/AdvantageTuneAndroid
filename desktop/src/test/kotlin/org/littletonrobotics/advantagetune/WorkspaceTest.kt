// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littletonrobotics.advantagetune.views.workspace.Workspace

class WorkspaceTest {
  @Test
  fun openedTopicReplacesTheOneBefore() {
    val workspace = Workspace()
    workspace.open("/A")
    workspace.open("/B")
    assertEquals(listOf("/B"), workspace.topics)
  }

  @Test
  fun pinnedTopicsStayOpen() {
    val workspace = Workspace()
    workspace.open("/A")
    workspace.setPinned("/A", true)
    workspace.open("/B")
    workspace.open("/C")
    assertEquals(listOf("/A", "/C"), workspace.topics)
    assertTrue(workspace.isPinned("/A"))
    assertFalse(workspace.isPinned("/C"))

    // Opening a pinned topic again leaves the others as they are
    workspace.open("/A")
    assertEquals(listOf("/A", "/C"), workspace.topics)
    assertEquals("/A", workspace.revealed?.first)
  }

  @Test
  fun unpinnedTopicStaysUntilAnotherIsOpened() {
    val workspace = Workspace()
    workspace.open("/A")
    workspace.setPinned("/A", true)
    workspace.open("/B")
    workspace.setPinned("/B", true)

    workspace.setPinned("/A", false)
    assertEquals(listOf("/A", "/B"), workspace.topics)
    workspace.open("/C")
    assertEquals(listOf("/B", "/C"), workspace.topics)
  }

  @Test
  fun closedTopicsAreRemoved() {
    val workspace = Workspace()
    workspace.open("/A")
    workspace.setPinned("/A", true)
    workspace.open("/B")

    workspace.close("/A")
    assertEquals(listOf("/B"), workspace.topics)
    workspace.close("/B")
    assertTrue(workspace.topics.isEmpty())
  }
}
