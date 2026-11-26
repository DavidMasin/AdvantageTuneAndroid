// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.store.ConnectionStatus
import org.littletonrobotics.advantagetune.store.NTStore

class NTStoreTest {
  private val queue = TestQueue()

  private fun demoStore(): NTStore {
    val store = NTStore(queue)
    store.serverHost = "demo"
    queue.runUntilIdle()
    return store
  }

  @Test
  fun parsesHostAndPort() {
    val store = NTStore(queue)
    assertEquals("robot.local", store.parsedHost)
    assertEquals(5810, store.parsedPort)

    store.serverHost = " 10.63.28.2:5811 "
    assertEquals("10.63.28.2", store.parsedHost)
    assertEquals(5811, store.parsedPort)

    store.serverHost = "localhost:99999"
    assertEquals("localhost", store.parsedHost)
    assertEquals(5810, store.parsedPort)

    store.serverHost = "[fe80::1]:5812"
    assertEquals("fe80::1", store.parsedHost)
    assertEquals(5812, store.parsedPort)

    store.serverHost = ""
    assertEquals("robot.local", store.parsedHost)
    assertFalse(store.isDemoMode)
  }

  @Test
  fun loadsDemoData() {
    val store = demoStore()

    assertTrue(store.isDemoMode)
    assertEquals(ConnectionStatus.Connected(latencyMs = 0.0), store.connectionStatus)
    assertEquals(listOf("Arrays", "Primitives", "Structs", "TeamNumber"), store.topicTree.map { it.name })
    assertEquals(TopicValue.IntValue(6328), store.latestValues["/TeamNumber"])
    assertEquals(
      "{x: 2.5, y: -1.75}",
      store.latestValues["/Structs/Translation2d"]?.formattedString,
    )
    assertEquals(
      "{mode: Teleop, enabled: true, estop: false, dsAttached: true, fmsAttached: false, errorCount: 0}",
      store.latestValues["/Structs/DriverStation"]?.formattedString,
    )
  }

  @Test
  fun tracksTunedValues() {
    val store = demoStore()
    val topic = store.topicsByName.getValue("/Primitives/Double")

    store.updateValue(topic, TopicValue.DoubleValue(4.0))
    store.updateValue(topic, TopicValue.DoubleValue(5.0))

    assertTrue(store.isPublished("/Primitives/Double"))
    assertEquals(TopicValue.DoubleValue(5.0), store.latestValues["/Primitives/Double"])
    assertEquals(TopicValue.DoubleValue(3.14159), store.tunedEntries["/Primitives/Double"]?.originalValue)

    store.revertAndUnpublishTopic("/Primitives/Double")
    assertFalse(store.isPublished("/Primitives/Double"))
    assertEquals(TopicValue.DoubleValue(3.14159), store.latestValues["/Primitives/Double"])
  }

  @Test
  fun keepsTunedValuesWhileDisconnected() {
    val store = demoStore()
    val topic = store.topicsByName.getValue("/Primitives/Integer")
    store.updateValue(topic, TopicValue.IntValue(7))

    store.disconnect()
    assertEquals(ConnectionStatus.Disconnected, store.connectionStatus)
    store.updateValue(topic, TopicValue.IntValue(8))
    assertEquals(TopicValue.IntValue(7), store.latestValues["/Primitives/Integer"])

    store.connect()
    queue.runUntilIdle()
    assertEquals(TopicValue.IntValue(8), store.latestValues["/Primitives/Integer"])
    assertEquals(1, store.tunedEntries.size)
  }

  @Test
  fun searchesTopics() {
    val store = demoStore()

    assertEquals(listOf<String>(), store.searchTopics("").map { it.name })
    assertEquals(
      listOf("/Arrays/DoubleArray", "/Primitives/Double"),
      store.searchTopics("double").map { it.name },
    )
    assertEquals(
      listOf("/Primitives/Double"),
      store.searchTopics("double", filter = "Numeric").map { it.name },
    )
    assertEquals(5, store.searchTopics("struct", filter = "Struct/Proto").size)
    assertNull(store.node("/Missing"))
    assertEquals(6, store.node("/Primitives")?.children?.size)
  }

  @Test
  fun clearsTopicsWhenLeavingDemoMode() {
    val store = demoStore()
    store.serverHost = "dem"
    queue.runUntilIdle()

    assertEquals(ConnectionStatus.Disconnected, store.connectionStatus)
    assertTrue(store.topicTree.isEmpty())
    assertTrue(store.latestValues.isEmpty())
  }
}
