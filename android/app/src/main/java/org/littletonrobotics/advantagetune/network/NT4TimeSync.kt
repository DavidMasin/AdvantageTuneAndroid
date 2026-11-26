// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.network

import kotlin.math.max

class NT4TimeSync {
  private var serverTimeOffsetUs: Long = 0
  private var networkLatencyUs: Long = 0
  private var smoothedLatencyMs: Double = 0.0
  private var hasSynced: Boolean = false

  /** Exponential moving average smoothing factor */
  private val smoothingFactor: Double = 0.1

  /** Result of [createPingValue]: the RTT ping frame `[-1, 0, 2, client_time_us]` */
  data class PingValue(val topicId: Int, val timestampUs: Long, val typeIdx: Int, val value: Long)

  /** Generates the payload for an outgoing RTT ping frame `[-1, 0, 2, client_time_us]` */
  fun createPingValue(): PingValue {
    return PingValue(-1, 0, 2, clientTimeUs())
  }

  /**
   * Handles an incoming RTT pong frame from the server: `[-1, server_time_us, 2,
   * client_send_time_us]`
   */
  fun handlePong(serverTimestampUs: Long, clientSendTimestampUs: Long): Double {
    val clientReceiveUs = clientTimeUs()
    val rttUs = max(0, clientReceiveUs - clientSendTimestampUs)
    val sampleLatencyMs = rttUs.toDouble() / 2000.0 // Instantaneous one-way latency
    val serverEstimateUs = serverTimestampUs + (rttUs / 2)
    val offsetUs = serverEstimateUs - clientReceiveUs

    this.networkLatencyUs = rttUs / 2
    this.serverTimeOffsetUs = offsetUs

    if (!hasSynced) {
      this.smoothedLatencyMs = sampleLatencyMs
      this.hasSynced = true
    } else {
      this.smoothedLatencyMs =
        (smoothingFactor * sampleLatencyMs) + ((1.0 - smoothingFactor) * this.smoothedLatencyMs)
    }

    return this.smoothedLatencyMs
  }

  val latencyMs: Double
    get() = smoothedLatencyMs

  val serverTimeUs: Long
    get() {
      if (!hasSynced) {
        return 0
      }
      return clientTimeUs() + serverTimeOffsetUs
    }

  val isSynced: Boolean
    get() = hasSynced

  fun reset() {
    serverTimeOffsetUs = 0
    networkLatencyUs = 0
    smoothedLatencyMs = 0.0
    hasSynced = false
  }

  companion object {
    // Wall clock time with the resolution of the monotonic clock
    private val clockOffsetUs = System.currentTimeMillis() * 1000 - System.nanoTime() / 1000

    fun clientTimeUs(): Long {
      return clockOffsetUs + System.nanoTime() / 1000
    }
  }
}
