// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.views.MainViewModel
import org.littletonrobotics.advantagetune.views.components.BackDispatcher
import org.littletonrobotics.advantagetune.views.workspace.Workspace

/**
 * Uses the app the way it is used on a touch screen, with the demo data. Windows delivers touches
 * as mouse events, so the mouse stands in for the finger.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAppTest {
  private val backDispatcher = BackDispatcher()
  private val workspace = Workspace()
  private lateinit var defaultLocale: Locale

  @Before
  fun useEnglish() {
    defaultLocale = Locale.getDefault()
    Locale.setDefault(Locale.US)
  }

  @After
  fun restoreLocale() {
    Locale.setDefault(defaultLocale)
  }

  private fun ComposeUiTest.launch(): MainViewModel {
    lateinit var viewModel: MainViewModel
    runOnUiThread { viewModel = MainViewModel() }
    setContent {
      DesktopApp(viewModel = viewModel, backDispatcher = backDispatcher, workspace = workspace)
    }
    runOnUiThread { viewModel.store.serverHost = "demo" }
    waitUntil(timeoutMillis = 10_000) { viewModel.store.topicTree.isNotEmpty() }
    waitForIdle()
    return viewModel
  }

  private fun ComposeUiTest.click(text: String) {
    onAllNodesWithText(text).onFirst().performClick()
    waitForIdle()
  }

  private fun ComposeUiTest.navigateBack() {
    runOnUiThread { assertTrue(backDispatcher.navigateBack()) }
    waitForIdle()
  }

  private fun ComposeUiTest.isShown(text: String): Boolean {
    waitForIdle()
    return try {
      onNodeWithText(text).isDisplayed()
    } catch (e: AssertionError) {
      false
    }
  }

  /** Drags a node the way a finger does, in many small steps. */
  private fun ComposeUiTest.drag(node: SemanticsNodeInteraction, step: Offset, steps: Int = 15) {
    node.performMouseInput {
      moveTo(center)
      press()
      repeat(steps) { moveBy(step) }
      release()
    }
    // Let the fling come to rest
    mainClock.advanceTimeBy(3000)
    waitForIdle()
  }

  private fun MouseInjectionScope.pressAt(position: Offset) {
    moveTo(position)
    press()
  }

  @Test
  fun stepButtonsPublishValues() = runDesktopComposeUiTest(width = 480, height = 860) {
    val viewModel = launch()
    click("Primitives")
    click("Double")
    click("+1.0")

    // The value moves to the next multiple of the step
    runOnUiThread {
      assertEquals(TopicValue.DoubleValue(4.0), viewModel.store.latestValues["/Primitives/Double"])
      assertEquals(setOf("/Primitives/Double"), viewModel.store.tunedEntries.keys.toSet())
    }

    // The Escape key goes back through this dispatcher
    navigateBack()
    onNodeWithText("Keys (6)").assertExists()
  }

  @Test
  fun wideWindowOpensTopicsNextToLists() = runDesktopComposeUiTest(width = 1280, height = 800) {
    launch()
    click("Primitives")
    click("Double")

    // The list stays where it is, and the topic is shown next to it
    assertTrue(isShown("Keys (6)"))
    assertTrue(isShown("Topic Details"))
    assertEquals(listOf("/Primitives/Double"), workspace.topics)

    // The next topic takes its place
    click("Integer")
    assertEquals(listOf("/Primitives/Integer"), workspace.topics)

    onNodeWithContentDescription("Close").performClick()
    waitForIdle()
    assertTrue(workspace.topics.isEmpty())
    assertFalse(isShown("Topic Details"))
  }

  @Test
  fun pinnedTopicsStayOpen() = runDesktopComposeUiTest(width = 1280, height = 800) {
    val viewModel = launch()
    click("Primitives")
    click("Double")
    onNodeWithContentDescription("Pin").performClick()
    waitForIdle()
    click("Integer")

    assertEquals(listOf("/Primitives/Double", "/Primitives/Integer"), workspace.topics)
    onAllNodesWithText("Topic Details").assertCountEquals(2)

    // Both can be edited
    click("+1.0")
    click("+1")
    runOnUiThread {
      val values = viewModel.store.latestValues
      assertEquals(TopicValue.DoubleValue(4.0), values["/Primitives/Double"])
      assertEquals(TopicValue.IntValue(43), values["/Primitives/Integer"])
    }

    // The tabs do not change which topics are open
    click("Search")
    assertEquals(listOf("/Primitives/Double", "/Primitives/Integer"), workspace.topics)
    onAllNodesWithText("Topic Details").assertCountEquals(2)

    // Without the pin, both make room for the next topic
    onNodeWithContentDescription("Unpin").performClick()
    waitForIdle()
    assertEquals(listOf("/Primitives/Double", "/Primitives/Integer"), workspace.topics)
    click("Browse")
    click("Float")
    assertEquals(listOf("/Primitives/Float"), workspace.topics)
  }

  @Test
  fun typedValuesArePublished() = runDesktopComposeUiTest(width = 1280, height = 800) {
    val viewModel = launch()
    click("Structs")
    click("MotorConfig")

    val field = onAllNodes(hasSetTextAction())[0]
    field.performTextReplacement("-12.5")
    field.performImeAction()
    waitForIdle()

    runOnUiThread {
      val value = viewModel.store.latestValues["/Structs/MotorConfig"]?.formattedString
      assertTrue(value, value!!.startsWith("{targetVelocity: -12.5,"))
    }
  }

  @Test
  fun secondaryClickOpensContextMenu() = runDesktopComposeUiTest(width = 480, height = 860) {
    val viewModel = launch()
    click("Primitives")

    // Windows sends this click when a finger is held on the screen
    onNodeWithText("Integer").performMouseInput { rightClick() }
    waitForIdle()
    click("Zero")

    runOnUiThread {
      assertEquals(TopicValue.IntValue(0), viewModel.store.latestValues["/Primitives/Integer"])
    }
  }

  @Test
  fun draggingScrollsFromAnyContent() = runDesktopComposeUiTest(width = 480, height = 500) {
    val viewModel = launch()
    click("Arrays")

    val starts =
      listOf<ComposeUiTest.() -> SemanticsNodeInteraction>(
        { onNodeWithText("Live Telemetry") },
        { onAllNodesWithText("+1.0").onFirst() },
        { onAllNodes(hasSetTextAction())[0] },
      )
    for (start in starts) {
      click("DoubleArray")
      assertTrue(isShown("Topic Details"))
      drag(start(), step = Offset(1f, -12f))
      assertFalse(isShown("Topic Details"))
      navigateBack()
    }

    // The drags that started on a button did not press it
    runOnUiThread { assertTrue(viewModel.store.tunedEntries.isEmpty()) }
  }

  @Test
  fun unsteadyTapStillClicks() = runDesktopComposeUiTest(width = 480, height = 860) {
    val viewModel = launch()
    click("Primitives")
    click("Double")

    onNodeWithText("+1.0").performMouseInput {
      pressAt(center)
      moveBy(Offset(2f, 3f))
      moveBy(Offset(3f, 2f))
      release()
    }
    waitForIdle()

    runOnUiThread {
      assertEquals(setOf("/Primitives/Double"), viewModel.store.tunedEntries.keys.toSet())
    }
  }

  @Test
  fun swipingRowRevealsActions() = runDesktopComposeUiTest(width = 480, height = 860) {
    val viewModel = launch()
    click("Primitives")
    click("Double")
    click("+1.0")
    navigateBack()
    navigateBack()
    click("Tuning")

    onNodeWithText("/Primitives/Double").performMouseInput {
      pressAt(Offset(width - 20f, height / 2f))
      repeat(12) { moveBy(Offset(-15f, 0f)) }
      release()
    }
    mainClock.advanceTimeBy(1000)
    waitForIdle()
    click("Unpublish")
    mainClock.advanceTimeBy(1000)
    waitForIdle()

    runOnUiThread { assertTrue(viewModel.store.tunedEntries.isEmpty()) }
  }
}
