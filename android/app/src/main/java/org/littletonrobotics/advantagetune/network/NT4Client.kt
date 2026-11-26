// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.littletonrobotics.advantagetune.models.NT4ClientMessage
import org.littletonrobotics.advantagetune.models.NT4Constants
import org.littletonrobotics.advantagetune.models.NT4SubscriptionOptions
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicValue

interface NT4ClientDelegate {
  fun clientDidConnect(client: NT4Client)

  fun clientDidSyncTime(client: NT4Client)

  fun clientDidDisconnect(client: NT4Client, error: Throwable?)

  fun clientDidAnnounceTopics(client: NT4Client, topics: List<NT4Topic>)

  fun clientDidUnannounceTopic(client: NT4Client, topicName: String, topicId: Int)

  fun clientDidUpdateProperties(
    client: NT4Client,
    topicName: String,
    properties: Map<String, String>,
  )

  fun clientDidReceiveValues(client: NT4Client, values: List<NT4ValueUpdate>)

  fun clientDidUpdateLatencyMs(client: NT4Client, latencyMs: Double)
}

data class NT4ValueUpdate(
  val topicId: Int,
  val topicName: String,
  val topicType: String,
  val timestampUs: Long,
  val value: TopicValue,
)

/**
 * NetworkTables 4 client. All methods must be called on the [MainQueue], which is also where the
 * delegate is called.
 */
class NT4Client(private val queue: MainQueue = MainQueue.Main) {
  var delegate: NT4ClientDelegate? = null

  private var socket: WebSocket? = null
  private var rttSocket: WebSocket? = null
  private val timeSync = NT4TimeSync()

  private var currentHost: String = NT4Constants.defaultHost
  private var currentPort: Int = NT4Constants.defaultPort
  private var isConnectionRequested: Boolean = false
  private var isConnected: Boolean = false
  private var isRttConnected: Boolean = false
  private var isV41: Boolean = false

  val isTimeSynced: Boolean
    get() = isConnected && timeSync.isSynced

  // Heartbeat & timeout tracking
  private var lastReceivedTime: Double = Double.NEGATIVE_INFINITY
  private val rttPeriodV40: Double = 1.0
  private val rttPeriodV41: Double = 0.25
  private val serverTimeoutV40: Double = 5.0
  private val serverTimeoutV41: Double = 1.0
  private val connectTimeoutInterval: Double = 3.0 // Timeout for initial TCP/mDNS handshake

  private var heartbeatTimer: ScheduledTimer? = null
  private var connectWatchdogTimer: ScheduledTimer? = null
  private var reconnectTimer: ScheduledTimer? = null

  private val subscriptions: MutableMap<Int, NT4SubscriptionOptions> = HashMap()
  private var publishedTopics: MutableMap<String, NT4Topic> = HashMap()
  private val serverTopicsById: MutableMap<Int, NT4Topic> = HashMap()
  private val serverTopicsByName: MutableMap<String, NT4Topic> = HashMap()

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout((connectTimeoutInterval * 1000).toLong(), TimeUnit.MILLISECONDS)
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .build()
  }

  private val currentTimeoutInterval: Double
    get() = if (isV41 && isRttConnected && rttSocket != null) serverTimeoutV41 else serverTimeoutV40

  private val currentPingPeriod: Double
    get() = if (isV41 && isRttConnected && rttSocket != null) rttPeriodV41 else rttPeriodV40

  // MARK: - Connection Management

  fun connect(host: String = NT4Constants.defaultHost, port: Int = NT4Constants.defaultPort) {
    this.currentHost = host
    this.currentPort = port
    this.isConnectionRequested = true

    startConnectionAttempt()
  }

  fun disconnect() {
    this.isConnectionRequested = false
    this.isConnected = false
    this.isRttConnected = false
    this.isV41 = false
    stopAllTimers()
    timeSync.reset()
    serverTopicsById.clear()
    serverTopicsByName.clear()
    publishedTopics.clear()
    subscriptions.clear()

    val oldSocket = socket
    socket = null

    val oldRttSocket = rttSocket
    rttSocket = null

    oldSocket?.disconnect()
    oldRttSocket?.disconnect()

    delegate?.clientDidDisconnect(this, error = null)
  }

  private fun startConnectionAttempt() {
    if (!isConnectionRequested) {
      return
    }

    // Clean up old sockets safely by detaching them first
    val oldSocket = socket
    socket = null

    val oldRttSocket = rttSocket
    rttSocket = null
    this.isRttConnected = false
    this.isV41 = false

    oldSocket?.disconnect()
    oldRttSocket?.disconnect()

    val request = makeRequest(NT4Constants.defaultSubprotocols)
    if (request == null) {
      scheduleReconnect()
      return
    }

    this.socket = httpClient.newWebSocket(request, SocketListener())

    // Arm connection handshake timeout watchdog
    queue.async {
      connectWatchdogTimer?.invalidate()
      connectWatchdogTimer =
        queue.schedule(connectTimeoutInterval, repeats = false) {
          val connected = isConnected
          val requested = isConnectionRequested

          if (requested && !connected) {
            handleDisconnect(error = IOException("Connection handshake timed out"))
          }
        }
    }
  }

  private fun startRttSocket() {
    val request = makeRequest(NT4Constants.subprotocolRTT) ?: return

    val oldRtt = this.rttSocket
    this.isRttConnected = false
    this.rttSocket = httpClient.newWebSocket(request, SocketListener())

    oldRtt?.disconnect()
  }

  private fun makeRequest(subprotocols: String): Request? {
    val cleanHost = currentHost.trim()
    if (cleanHost.isEmpty()) return null

    return try {
      Request.Builder()
        .url("ws://$cleanHost:$currentPort/nt/${NT4Constants.appName}")
        .header("Sec-WebSocket-Protocol", subprotocols)
        .build()
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  private fun scheduleReconnect() {
    queue.async {
      val requested = isConnectionRequested
      val connected = isConnected

      if (requested && !connected) {
        reconnectTimer?.invalidate()
        reconnectTimer = queue.schedule(1.5, repeats = false) { startConnectionAttempt() }
      }
    }
  }

  private fun startHeartbeat() {
    queue.async {
      connectWatchdogTimer?.invalidate()
      connectWatchdogTimer = null

      heartbeatTimer?.invalidate()
      val period = currentPingPeriod

      heartbeatTimer = queue.schedule(period, repeats = true) { checkHeartbeatAndPing() }
    }
  }

  private fun stopAllTimers() {
    queue.async {
      heartbeatTimer?.invalidate()
      heartbeatTimer = null
      connectWatchdogTimer?.invalidate()
      connectWatchdogTimer = null
      reconnectTimer?.invalidate()
      reconnectTimer = null
    }
  }

  private fun checkHeartbeatAndPing() {
    if (!isConnected) {
      return
    }

    val timeSinceLastRx = now() - lastReceivedTime
    val timeout = currentTimeoutInterval
    if (timeSinceLastRx > timeout) {
      val timeoutError =
        IOException(
          "Server heartbeat timed out (no response for ${"%.1f".format(timeSinceLastRx)}s)"
        )
      handleDisconnect(error = timeoutError)
      return
    }

    sendRttPing()
  }

  // MARK: - Subscriptions & Publishing

  fun subscribe(topics: List<String>, subuid: Int, options: NT4SubscriptionOptions) {
    subscriptions[subuid] = options

    val msg = NT4ClientMessage.Subscribe(topics = topics, subuid = subuid, options = options)
    sendClientMessage(msg)
  }

  fun unsubscribe(subuid: Int) {
    subscriptions.remove(subuid)

    val msg = NT4ClientMessage.Unsubscribe(subuid = subuid)
    sendClientMessage(msg)
  }

  fun publish(name: String, type: String, pubuid: Int, properties: Map<String, String> = mapOf()) {
    val topic =
      NT4Topic(id = pubuid, name = name, type = type, properties = properties, pubuid = pubuid)
    publishedTopics[name] = topic

    val msg =
      NT4ClientMessage.Publish(name = name, type = type, pubuid = pubuid, properties = properties)
    sendClientMessage(msg)
  }

  fun unpublish(pubuid: Int) {
    publishedTopics = publishedTopics.filterTo(HashMap()) { it.value.pubuid != pubuid }

    val msg = NT4ClientMessage.Unpublish(pubuid = pubuid)
    sendClientMessage(msg)
  }

  fun setValue(value: TopicValue, pubuid: Int, typeIndex: Int) {
    val timestampUs = timeSync.serverTimeUs
    val payload =
      MessagePackStream.Value.ArrayValue(
        listOf(
          MessagePackStream.Value.IntValue(pubuid.toLong()),
          MessagePackStream.Value.IntValue(timestampUs),
          MessagePackStream.Value.IntValue(typeIndex.toLong()),
          value.toMessagePackStreamValue(),
        )
      )

    val data = MessagePackStream.pack(payload)
    if (isConnected) {
      socket?.send(data)
    }
  }

  private fun sendClientMessage(message: NT4ClientMessage) {
    val string = message.toJSONString() ?: return
    if (isConnected) {
      socket?.send(string)
    }
  }

  private fun sendRttPing() {
    if (!isConnected) {
      return
    }
    val ping = timeSync.createPingValue()
    val targetSocket = (if (isRttConnected) rttSocket else null) ?: socket

    val pingArray =
      MessagePackStream.Value.ArrayValue(
        listOf(
          MessagePackStream.Value.IntValue(ping.topicId.toLong()),
          MessagePackStream.Value.IntValue(ping.timestampUs),
          MessagePackStream.Value.IntValue(ping.typeIdx.toLong()),
          MessagePackStream.Value.IntValue(ping.value),
        )
      )
    val data = MessagePackStream.pack(pingArray)
    targetSocket?.send(data)
  }

  // MARK: - WebSocket Events

  private sealed class WebSocketEvent {
    data class Connected(val protocolHeader: String) : WebSocketEvent()

    data class Disconnected(val reason: String, val code: Int) : WebSocketEvent()

    data class Text(val string: String) : WebSocketEvent()

    data class Binary(val data: ByteString) : WebSocketEvent()

    data class Error(val error: Throwable?) : WebSocketEvent()

    data object PeerClosed : WebSocketEvent()
  }

  /** Forwards events from the socket's reader thread onto the queue. */
  private inner class SocketListener : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      val protocolHeader = response.header("Sec-WebSocket-Protocol") ?: ""
      queue.async { didReceive(WebSocketEvent.Connected(protocolHeader), webSocket) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      queue.async { didReceive(WebSocketEvent.Text(text), webSocket) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
      queue.async { didReceive(WebSocketEvent.Binary(bytes), webSocket) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(1000, null)
      queue.async { didReceive(WebSocketEvent.Disconnected(reason, code), webSocket) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      queue.async { didReceive(WebSocketEvent.PeerClosed, webSocket) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      queue.async { didReceive(WebSocketEvent.Error(t), webSocket) }
    }
  }

  private fun didReceive(event: WebSocketEvent, client: WebSocket) {
    val isPrimary = (client === socket)
    val isRTT = (client === rttSocket)
    if (!isPrimary && !isRTT) {
      return
    }

    if (isRTT) {
      handleRttSocketEvent(event)
    } else {
      handlePrimarySocketEvent(event)
    }
  }

  private fun handleRttSocketEvent(event: WebSocketEvent) {
    when (event) {
      is WebSocketEvent.Connected -> {
        isRttConnected = true
        lastReceivedTime = now()
        sendRttPing()
        startHeartbeat()
      }

      is WebSocketEvent.Binary -> {
        lastReceivedTime = now()
        handleBinaryMessage(event.data)
      }

      is WebSocketEvent.Disconnected,
      is WebSocketEvent.Error,
      is WebSocketEvent.PeerClosed -> {
        isRttConnected = false
        rttSocket = null
        startHeartbeat() // Fall back to primary socket ping schedule
      }

      is WebSocketEvent.Text -> {}
    }
  }

  private fun handlePrimarySocketEvent(event: WebSocketEvent) {
    when (event) {
      is WebSocketEvent.Connected -> {
        val negotiatedV41 = event.protocolHeader.contains(NT4Constants.subprotocolV41)

        isConnected = true
        isV41 = negotiatedV41
        lastReceivedTime = now()
        serverTopicsById.clear()
        serverTopicsByName.clear()

        queue.async {
          reconnectTimer?.invalidate()
          reconnectTimer = null
          connectWatchdogTimer?.invalidate()
          connectWatchdogTimer = null
        }

        // Always send an immediate RTT ping on the primary socket so time sync
        // completes in a single round-trip without waiting for the secondary RTT socket handshake.
        sendRttPing()
        if (negotiatedV41) {
          startRttSocket()
        }

        startHeartbeat()
        delegate?.clientDidConnect(this)
      }

      is WebSocketEvent.Disconnected ->
        handleDisconnect(error = IOException("${event.reason} (${event.code})"))

      is WebSocketEvent.Text -> {
        lastReceivedTime = now()
        handleTextMessage(event.string)
      }

      is WebSocketEvent.Binary -> {
        lastReceivedTime = now()
        handleBinaryMessage(event.data)
      }

      is WebSocketEvent.Error -> handleDisconnect(error = event.error)

      is WebSocketEvent.PeerClosed -> handleDisconnect(error = null)
    }
  }

  private fun handleDisconnect(error: Throwable?) {
    val wasConnected = isConnected
    val requested = isConnectionRequested
    isConnected = false
    isRttConnected = false
    isV41 = false
    timeSync.reset()
    serverTopicsById.clear()
    serverTopicsByName.clear()
    publishedTopics.clear()
    subscriptions.clear()

    val oldSocket = socket
    socket = null

    val oldRttSocket = rttSocket
    rttSocket = null

    oldSocket?.disconnect()
    oldRttSocket?.disconnect()

    stopAllTimers()

    if (wasConnected || requested) {
      delegate?.clientDidDisconnect(this, error = error)
    }

    if (requested) {
      scheduleReconnect()
    }
  }

  // MARK: - Message Parsers

  private fun handleTextMessage(string: String) {
    val json =
      try {
        JSONArray(string)
      } catch (e: Exception) {
        return
      }

    val messages = (0 until json.length()).map { json.optJSONObject(it) ?: return }

    val announcedBatch = mutableListOf<NT4Topic>()

    for (msg in messages) {
      val method = msg.opt("method") as? String ?: continue
      val params = msg.optJSONObject("params") ?: continue

      when (method) {
        "announce" -> {
          val name = params.opt("name") as? String ?: continue
          val id = params.integer("id") ?: continue
          val type = params.opt("type") as? String ?: continue

          val props = params.optJSONObject("properties").toStringMap()
          val pubuid = params.integer("pubuid")

          val topic = NT4Topic(id = id, name = name, type = type, properties = props, pubuid = pubuid)

          serverTopicsById[id] = topic
          serverTopicsByName[name] = topic

          announcedBatch.add(topic)
        }

        "unannounce" -> {
          val name = params.opt("name") as? String ?: continue
          val id = params.integer("id") ?: continue

          serverTopicsById.remove(id)
          serverTopicsByName.remove(name)

          delegate?.clientDidUnannounceTopic(this, topicName = name, topicId = id)
        }

        "properties" -> {
          val name = params.opt("name") as? String ?: continue
          val update = params.optJSONObject("update") ?: continue

          val props = update.toStringMap()

          val existing = serverTopicsByName[name]
          if (existing != null) {
            val updated = existing.copy(properties = existing.properties + props)
            serverTopicsByName[name] = updated
            serverTopicsById[updated.id] = updated
          }

          delegate?.clientDidUpdateProperties(this, topicName = name, properties = props)
        }

        else -> {}
      }
    }

    if (announcedBatch.isNotEmpty()) {
      delegate?.clientDidAnnounceTopics(this, topics = announcedBatch)
    }
  }

  private fun handleBinaryMessage(data: ByteString) {
    val unpackedValues = MessagePackStream.unpackAll(data)
    val valueBatch = mutableListOf<NT4ValueUpdate>()

    for (value in unpackedValues) {
      if (value !is MessagePackStream.Value.ArrayValue) continue
      val elements = value.elements
      if (elements.size < 4) continue
      val topicId = elements[0].asInt64?.toInt() ?: continue
      val timestampUs = elements[1].asInt64 ?: continue
      val typeIdx = elements[2].asInt64?.toInt() ?: continue

      val rawVal = elements[3]

      if (topicId == -1) {
        val wasSynced = timeSync.isSynced
        val clientSendUs = rawVal.asInt64 ?: 0
        val latency =
          timeSync.handlePong(serverTimestampUs = timestampUs, clientSendTimestampUs = clientSendUs)
        if (!wasSynced && timeSync.isSynced) {
          delegate?.clientDidSyncTime(this)
        }
        delegate?.clientDidUpdateLatencyMs(this, latencyMs = latency)
      } else {
        val topic = serverTopicsById[topicId]

        val topicValue = TopicValue.from(typeIndex = typeIdx, value = rawVal)
        val topicName = topic?.name ?: ""
        val topicType = topic?.type ?: ""
        valueBatch.add(
          NT4ValueUpdate(
            topicId = topicId,
            topicName = topicName,
            topicType = topicType,
            timestampUs = timestampUs,
            value = topicValue,
          )
        )
      }
    }

    if (valueBatch.isNotEmpty()) {
      delegate?.clientDidReceiveValues(this, values = valueBatch)
    }
  }

  // MARK: - Helpers

  /** Monotonic time in seconds. */
  private fun now(): Double = System.nanoTime() / 1e9

  /** Closes the socket, like a socket that has had its delegate removed. */
  private fun WebSocket.disconnect() {
    if (!close(1000, null)) {
      cancel()
    }
  }

  /** The value as an integer, or null if it is missing or not an integral number. */
  private fun JSONObject.integer(key: String): Int? {
    val value = opt(key)
    if (value is Int) return value
    if (value is Long && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) return value.toInt()
    if (value is Double && value == Math.rint(value) && !value.isInfinite()) return value.toInt()
    return null
  }

  private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return mapOf()
    val result = HashMap<String, String>()
    for (key in keys()) {
      result[key] = "${opt(key)}"
    }
    return result
  }
}
