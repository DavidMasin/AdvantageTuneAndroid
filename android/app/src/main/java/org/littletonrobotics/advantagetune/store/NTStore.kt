// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.Date
import org.littletonrobotics.advantagetune.decoder.ProtoDecoder
import org.littletonrobotics.advantagetune.decoder.ProtoSchemaRegistry
import org.littletonrobotics.advantagetune.decoder.StructDecoder
import org.littletonrobotics.advantagetune.models.NT4Constants
import org.littletonrobotics.advantagetune.models.NT4SubscriptionOptions
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.models.TopicTreeBuilder
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.models.UserDefaults
import org.littletonrobotics.advantagetune.models.UserDefaultsKeys
import org.littletonrobotics.advantagetune.models.localizedStandardCompare
import org.littletonrobotics.advantagetune.network.MainQueue
import org.littletonrobotics.advantagetune.network.NT4Client
import org.littletonrobotics.advantagetune.network.NT4ClientDelegate
import org.littletonrobotics.advantagetune.network.NT4ValueUpdate

sealed class ConnectionStatus {
  data object Disconnected : ConnectionStatus()

  data object Connecting : ConnectionStatus()

  data class Connected(val latencyMs: Double) : ConnectionStatus()

  val isConnected: Boolean
    get() = this is Connected
}

data class TunedEntry(
  val topicPath: String,
  val topicType: String,
  val currentValue: TopicValue,
  val originalValue: TopicValue? = null,
  val pubuid: Int,
  val properties: Map<String, String> = mapOf(),
  val lastModified: Date = Date(),
) {
  val id: String
    get() = topicPath
}

/**
 * Observable state of the NetworkTables connection. All members must be used from the [MainQueue].
 */
class NTStore(private val queue: MainQueue = MainQueue.Main) : NT4ClientDelegate {
  private val client = NT4Client(queue)

  // MARK: - Server & Connection State
  private var serverHostState by
    mutableStateOf(UserDefaults.string(UserDefaultsKeys.serverHost) ?: NT4Constants.defaultHost)

  var serverHost: String
    get() = serverHostState
    set(value) {
      val oldValue = serverHostState
      serverHostState = value

      UserDefaults.set(value, UserDefaultsKeys.serverHost)
      if (isDemoMode) {
        connect()
      } else if (oldValue.trim().lowercase() == "demo") {
        disconnect()
        topicsByName.clear()
        topicsById.clear()
        latestValues.clear()
        valueTimestamps.clear()
        rebuildTree()
      }
    }

  val isDemoMode: Boolean
    get() = parsedHost.lowercase() == "demo"

  val parsedHost: String
    get() {
      val trimmed = serverHost.trim()
      if (trimmed.startsWith("[") && trimmed.contains("]")) {
        val closeBracket = trimmed.indexOf(']')
        val hostInside = trimmed.substring(1, closeBracket)
        return hostInside.ifEmpty { NT4Constants.defaultHost }
      }
      val colonIndex = trimmed.lastIndexOf(':')
      if (colonIndex >= 0) {
        val hostPart = trimmed.substring(0, colonIndex).trim()
        return hostPart.ifEmpty { NT4Constants.defaultHost }
      }
      return trimmed.ifEmpty { NT4Constants.defaultHost }
    }

  val parsedPort: Int
    get() {
      val trimmed = serverHost.trim()
      val colonIndex = trimmed.lastIndexOf(':')
      if (colonIndex >= 0) {
        val portPart = trimmed.substring(colonIndex + 1).trim()
        val customPort = portPart.toIntOrNull()
        if (customPort != null && customPort > 0 && customPort <= 65535) {
          return customPort
        }
      }
      return NT4Constants.defaultPort
    }

  var connectionStatus: ConnectionStatus by mutableStateOf(ConnectionStatus.Disconnected)
    private set

  var isConnectionRequested: Boolean by mutableStateOf(false)
    private set

  private var lastLatencyUpdate: Double = Double.NEGATIVE_INFINITY

  // MARK: - Data State
  val topicsByName: SnapshotStateMap<String, NT4Topic> = mutableStateMapOf()
  private val topicsById: MutableMap<Int, NT4Topic> = HashMap()
  val latestValues: SnapshotStateMap<String, TopicValue> = mutableStateMapOf()
  private val valueTimestamps: MutableMap<String, Date> = HashMap()

  var topicTree: List<TopicNode> by mutableStateOf(listOf())
    private set

  // MARK: - Publishing & Tuning
  val tunedEntries: SnapshotStateMap<String, TunedEntry> = mutableStateMapOf() // Path -> TunedEntry
  private var nextPubUid: Int = 0
  private var nextSubUid: Int = 0

  fun isPublished(topicName: String): Boolean {
    return tunedEntries[topicName] != null
  }

  val publishedTopics: Map<String, Int>
    get() = tunedEntries.mapValues { it.value.pubuid }

  // MARK: - Subscription Tracking
  private class ActiveSub(val subuid: Int, var refCount: Int)

  private var activePathSubs: MutableMap<String, ActiveSub> = HashMap()

  // Rate-limit tree rebuilding
  private var isTreeUpdatePending = false

  init {
    client.delegate = this
  }

  // MARK: - Connection Actions

  fun connect() {
    isConnectionRequested = true
    if (isDemoMode) {
      client.disconnect()
      loadDemoMode()
      return
    }
    connectionStatus = ConnectionStatus.Connecting
    client.connect(host = parsedHost, port = parsedPort)
  }

  private fun loadDemoMode() {
    connectionStatus = ConnectionStatus.Connected(latencyMs = 0.0)
    topicsByName.clear()
    topicsById.clear()
    latestValues.clear()
    valueTimestamps.clear()

    queue.async {
      val demoData = DemoDataProvider.loadDemoData()
      if (!(isDemoMode && isConnectionRequested)) return@async

      val now = Date()
      for (topic in demoData.topics) {
        topicsByName[topic.name] = topic
        topicsById[topic.id] = topic
      }
      for ((name, value) in demoData.values) {
        latestValues[name] = value
        valueTimestamps[name] = now
      }
      rebuildTree()
      republishTunedValues()
    }
  }

  fun disconnect() {
    isConnectionRequested = false
    connectionStatus = ConnectionStatus.Disconnected
    if (!isDemoMode) {
      client.disconnect()
    }
  }

  fun reconnect() {
    disconnect()
    connect()
  }

  // MARK: - Subscription Management

  /** Acquires an on-demand streaming subscription for an exact topic. */
  fun acquireSubscription(topicName: String) {
    if (topicName.isEmpty()) return

    val existing = activePathSubs[topicName]
    if (existing != null) {
      existing.refCount += 1
    } else {
      val subuid = nextSubUid
      nextSubUid += 1
      activePathSubs[topicName] = ActiveSub(subuid = subuid, refCount = 1)

      if (connectionStatus.isConnected && !isDemoMode && client.isTimeSynced) {
        val options =
          NT4SubscriptionOptions(periodic = 0.02, all = false, topicsonly = false, prefix = false)
        client.subscribe(topics = listOf(topicName), subuid = subuid, options = options)
      }
    }
  }

  /** Acquires on-demand streaming subscriptions for a batch of exact topic names. */
  fun acquireSubscriptions(topicNames: List<String>) {
    for (name in topicNames) {
      acquireSubscription(name)
    }
  }

  /** Releases the on-demand streaming subscription for an exact topic. */
  fun releaseSubscription(topicName: String) {
    val existing = activePathSubs[topicName] ?: return
    existing.refCount -= 1

    if (existing.refCount <= 0) {
      activePathSubs.remove(topicName)
      if (connectionStatus.isConnected && !isDemoMode && client.isTimeSynced) {
        client.unsubscribe(subuid = existing.subuid)
      }
    }
  }

  /** Releases on-demand streaming subscriptions for a batch of exact topic names. */
  fun releaseSubscriptions(topicNames: List<String>) {
    for (name in topicNames) {
      releaseSubscription(name)
    }
  }

  // MARK: - Value Publishing & Editing

  private val canPublishToServer: Boolean
    get() = connectionStatus.isConnected && (isDemoMode || client.isTimeSynced)

  /** Updates a topic value. If not published, automatically publishes it first. */
  fun updateValue(topic: NT4Topic, newValue: TopicValue) {
    val topicName = topic.name
    val now = Date()

    val pubuid: Int
    val orig: TopicValue?
    val mergedProps: Map<String, String>

    val existing = tunedEntries[topicName]
    if (existing != null) {
      pubuid = existing.pubuid
      orig = existing.originalValue ?: latestValues[topicName]
      mergedProps = existing.properties + topic.properties
    } else {
      pubuid = nextPubUid
      nextPubUid += 1
      orig = latestValues[topicName]
      mergedProps = topic.properties
      if (!isDemoMode && connectionStatus.isConnected && client.isTimeSynced) {
        client.publish(name = topicName, type = topic.type, pubuid = pubuid, properties = mergedProps)
      }
    }

    // Record in tuned entries
    tunedEntries[topicName] =
      TunedEntry(
        topicPath = topicName,
        topicType = topic.type,
        currentValue = newValue,
        originalValue = orig,
        pubuid = pubuid,
        properties = mergedProps,
        lastModified = now,
      )

    // Send new value to server (if disconnected or time sync is pending, republishTunedValues will
    // publish on connect)
    if (!isDemoMode && connectionStatus.isConnected && client.isTimeSynced) {
      client.setValue(newValue, pubuid = pubuid, typeIndex = topic.typeIndex)
    }

    // Only update server value state when actively connected
    if (canPublishToServer) {
      latestValues[topicName] = newValue
      valueTimestamps[topicName] = now
    }
  }

  /** Reverts a topic's value back to its baseline server value. */
  fun revertValue(name: String) {
    val entry = tunedEntries[name] ?: return
    val orig = entry.originalValue ?: return

    // Push baseline value to server
    if (!isDemoMode && connectionStatus.isConnected && client.isTimeSynced) {
      val typeIndex = topicsByName[name]?.typeIndex ?: NT4Constants.typeIndex(entry.topicType)
      client.setValue(orig, pubuid = entry.pubuid, typeIndex = typeIndex)
    }

    val now = Date()
    if (canPublishToServer) {
      latestValues[name] = orig
      valueTimestamps[name] = now
    }

    tunedEntries[name] = entry.copy(currentValue = orig, lastModified = now)
  }

  /** Unpublishes a topic and removes it from tuned entries. */
  fun unpublishTopic(name: String) {
    val entry = tunedEntries.remove(name) ?: return
    if (!isDemoMode && connectionStatus.isConnected && client.isTimeSynced) {
      client.unpublish(pubuid = entry.pubuid)
    }
  }

  /** Convenience to revert value and unpublish topic. */
  fun revertAndUnpublishTopic(name: String) {
    val entry = tunedEntries[name]
    val orig = entry?.originalValue
    if (entry != null && orig != null) {
      if (!isDemoMode && connectionStatus.isConnected && client.isTimeSynced) {
        val typeIndex = NT4Constants.typeIndex(entry.topicType)
        client.setValue(orig, pubuid = entry.pubuid, typeIndex = typeIndex)
      }
      if (canPublishToServer) {
        latestValues[name] = orig
        valueTimestamps[name] = Date()
      }
    }
    unpublishTopic(name)
  }

  /**
   * Reverts all tuned values back to baseline server values, unpublishes them, and clears tuned
   * entries.
   */
  fun revertAllTunedTopics() {
    val topicNames = tunedEntries.keys.toList()
    for (name in topicNames) {
      revertAndUnpublishTopic(name)
    }
  }

  /** Unpublishes all currently published tuned topics and clears tuned entries. */
  fun unpublishAllTunedTopics() {
    val topicNames = tunedEntries.keys.toList()
    for (name in topicNames) {
      unpublishTopic(name)
    }
  }

  /** Clears all tuned values history. */
  fun clearTunedHistory() {
    unpublishAllTunedTopics()
  }

  // MARK: - Search

  fun searchTopics(query: String, filter: String = "All", limit: Int = 100): List<NT4Topic> {
    val clean = query.trim().lowercase()
    if (clean.isEmpty()) {
      return listOf() // Never return thousands of topics when query is empty
    }

    val matches = mutableListOf<NT4Topic>()

    for (topic in topicsByName.values) {
      if (topic.isSchema) continue

      // Filter match
      val matchesFilter: Boolean =
        when (filter) {
          "Numeric" -> (topic.type == "double" || topic.type == "float" || topic.type == "int")
          "Boolean" -> (topic.type == "boolean")
          "String" -> (topic.type == "string" || topic.type == "json")
          "Arrays" -> topic.isArray
          "Struct/Proto" -> (topic.isStruct || topic.isProtobuf)
          else -> true
        }

      if (!matchesFilter) continue

      // Query match
      if (topic.name.lowercase().contains(clean) || topic.type.lowercase().contains(clean)) {
        matches.add(topic)
        if (matches.size >= limit) break
      }
    }

    return matches.sortedWith { a, b -> localizedStandardCompare(a.name, b.name) }
  }

  // MARK: - Tree Lookups & Rebuilding

  fun node(path: String): TopicNode? {
    fun find(nodes: List<TopicNode>, target: String): TopicNode? {
      for (node in nodes) {
        if (node.path == target) return node
        if (node is TopicNode.Folder) {
          val found = find(node.children, target)
          if (found != null) {
            return found
          }
        }
      }
      return null
    }
    return find(topicTree, path)
  }

  private fun scheduleTreeUpdate() {
    if (isTreeUpdatePending) return
    isTreeUpdatePending = true

    queue.schedule(0.2) {
      isTreeUpdatePending = false
      rebuildTree()
    }
  }

  private fun rebuildTree() {
    this.topicTree =
      TopicTreeBuilder.buildTree(
        topics = topicsByName.values.toList(),
        values = latestValues,
        timestamps = valueTimestamps,
        hideSchemaTopics = true,
      )
  }

  /** Republishes all active tuned values upon connection or reconnection. */
  fun republishTunedValues() {
    for ((topicName, tunedEntry) in tunedEntries.toMap()) {
      val pubuid = nextPubUid
      nextPubUid += 1

      val topicType = tunedEntry.topicType
      val typeIndex = NT4Constants.typeIndex(topicType)
      val properties = tunedEntry.properties + (topicsByName[topicName]?.properties ?: mapOf())
      val entry = tunedEntry.copy(pubuid = pubuid, properties = properties)
      tunedEntries[topicName] = entry

      if (!isDemoMode && client.isTimeSynced) {
        client.publish(name = topicName, type = topicType, pubuid = pubuid, properties = properties)
        client.setValue(entry.currentValue, pubuid = pubuid, typeIndex = typeIndex)
      }

      latestValues[topicName] = entry.currentValue
      valueTimestamps[topicName] = Date()
    }
  }

  // MARK: - NT4ClientDelegate Callbacks

  override fun clientDidConnect(client: NT4Client) {
    queue.async {
      if (isDemoMode) return@async
      connectionStatus = ConnectionStatus.Connected(latencyMs = 0.0)
      lastLatencyUpdate = Double.NEGATIVE_INFINITY

      // Clear old session topics before receiving fresh announcements
      topicsByName.clear()
      topicsById.clear()
      rebuildTree()

      nextSubUid = 0
      nextPubUid = 0
    }
  }

  override fun clientDidSyncTime(client: NT4Client) {
    queue.async {
      if (isDemoMode) return@async
      if (!connectionStatus.isConnected) return@async

      // Republish all tuned values first (with a valid synced server timestamp)
      // before subscribing, so subsequent subscription snapshots from the server
      // already reflect the republished tuned values.
      republishTunedValues()

      // Subscribe to topic list
      val rootOpts =
        NT4SubscriptionOptions(periodic = 0.02, all = false, topicsonly = true, prefix = true)
      val rootSubUid = nextSubUid
      nextSubUid += 1
      this.client.subscribe(topics = listOf(""), subuid = rootSubUid, options = rootOpts)

      // Subscribe to schemas
      val schemaOpts =
        NT4SubscriptionOptions(periodic = 0.02, all = true, topicsonly = false, prefix = true)
      val schemaSubUid = nextSubUid
      nextSubUid += 1
      this.client.subscribe(
        topics = listOf("/.schema/", "/AdvantageKit/.schema/"),
        subuid = schemaSubUid,
        options = schemaOpts,
      )

      // Restore any active view subscriptions
      val remappedActiveSubs = HashMap<String, ActiveSub>()
      for ((path, activeSub) in activePathSubs) {
        val subuid = nextSubUid
        nextSubUid += 1
        remappedActiveSubs[path] = ActiveSub(subuid = subuid, refCount = activeSub.refCount)
        val options =
          NT4SubscriptionOptions(periodic = 0.02, all = false, topicsonly = false, prefix = false)
        this.client.subscribe(topics = listOf(path), subuid = subuid, options = options)
      }
      activePathSubs = remappedActiveSubs
    }
  }

  override fun clientDidDisconnect(client: NT4Client, error: Throwable?) {
    queue.async {
      if (isDemoMode) return@async
      lastLatencyUpdate = Double.NEGATIVE_INFINITY
      if (isConnectionRequested) {
        connectionStatus = ConnectionStatus.Connecting
      } else {
        connectionStatus = ConnectionStatus.Disconnected
      }
    }
  }

  override fun clientDidAnnounceTopics(client: NT4Client, topics: List<NT4Topic>) {
    queue.async {
      if (isDemoMode) return@async
      for (topic in topics) {
        topicsByName[topic.name] = topic
        topicsById[topic.id] = topic
      }
      scheduleTreeUpdate()
    }
  }

  override fun clientDidUnannounceTopic(client: NT4Client, topicName: String, topicId: Int) {
    queue.async {
      if (isDemoMode) return@async
      topicsByName.remove(topicName)
      topicsById.remove(topicId)
      latestValues.remove(topicName)
      valueTimestamps.remove(topicName)
      scheduleTreeUpdate()
    }
  }

  override fun clientDidUpdateProperties(
    client: NT4Client,
    topicName: String,
    properties: Map<String, String>,
  ) {
    queue.async {
      if (isDemoMode) return@async
      val existing = topicsByName[topicName]
      if (existing != null) {
        val topic = existing.copy(properties = existing.properties + properties)
        topicsByName[topicName] = topic
        topicsById[topic.id] = topic
        scheduleTreeUpdate()
      }
    }
  }

  override fun clientDidReceiveValues(client: NT4Client, values: List<NT4ValueUpdate>) {
    queue.async {
      if (isDemoMode) return@async
      val now = Date()
      for (item in values) {
        val topicName = item.topicName.ifEmpty { topicsById[item.topicId]?.name ?: "" }
        if (topicName.isEmpty()) continue
        val topicType = item.topicType.ifEmpty { topicsById[item.topicId]?.type ?: "" }

        var processedValue = item.value

        // Process schema registrations
        if (topicName.contains(".schema/struct:")) {
          val schemaName = topicName.split(".schema/struct:").last()
          if (item.value is TopicValue.RawValue) {
            StructDecoder.shared.addSchema(name = schemaName, schemaData = item.value.data)
          }
        } else if (topicName.contains(".schema/proto:")) {
          val schemaName = topicName.split(".schema/proto:").last()
          if (item.value is TopicValue.RawValue) {
            ProtoSchemaRegistry.shared.addDescriptor(name = schemaName, data = item.value.data)
          }
        }

        // Process struct / proto decoding for regular topics
        if (item.value is TopicValue.RawValue) {
          val rawData = item.value.data
          val isStruct = topicType.startsWith("struct:")
          val isProto = topicType.startsWith("proto:")
          if (isStruct) {
            val decoded = StructDecoder.shared.decode(typeName = topicType, data = rawData)
            if (decoded != null) {
              processedValue =
                TopicValue.Struct(typeName = topicType, value = decoded, raw = rawData)
            }
          } else if (isProto) {
            val decoded = ProtoDecoder.shared.decode(typeName = topicType, data = rawData)
            if (decoded != null) {
              processedValue =
                TopicValue.Struct(typeName = topicType, value = decoded, raw = rawData)
            } else {
              processedValue = TopicValue.ProtoRaw(typeName = topicType, data = rawData)
            }
          }
        }

        latestValues[topicName] = processedValue
        valueTimestamps[topicName] = now
      }
    }
  }

  override fun clientDidUpdateLatencyMs(client: NT4Client, latencyMs: Double) {
    queue.async {
      if (isDemoMode) return@async
      if (!connectionStatus.isConnected) return@async
      val now = System.nanoTime() / 1e9
      if (now - lastLatencyUpdate >= 1.0) {
        lastLatencyUpdate = now
        connectionStatus = ConnectionStatus.Connected(latencyMs = latencyMs)
      }
    }
  }
}
