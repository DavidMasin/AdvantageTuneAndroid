// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.network

import android.os.Handler
import android.os.Looper

/**
 * Serial queue that all NetworkTables state is confined to, the counterpart of
 * `DispatchQueue.main` and `Timer` on iOS.
 */
interface MainQueue {
  /** Runs the block after the work that is already queued. */
  fun async(block: () -> Unit)

  /** Runs the block once after the delay, or repeatedly at that interval. */
  fun schedule(intervalSeconds: Double, repeats: Boolean = false, block: () -> Unit): ScheduledTimer

  /** Queue backed by the Android main thread. */
  object Main : MainQueue {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun async(block: () -> Unit) {
      handler.post(block)
    }

    override fun schedule(
      intervalSeconds: Double,
      repeats: Boolean,
      block: () -> Unit,
    ): ScheduledTimer {
      val delayMs = (intervalSeconds * 1000).toLong()
      val timer =
        object : ScheduledTimer, Runnable {
          private var isValid = true

          override fun run() {
            if (!isValid) return
            if (repeats) {
              handler.postDelayed(this, delayMs)
            }
            block()
          }

          override fun invalidate() {
            isValid = false
            handler.removeCallbacks(this)
          }
        }
      handler.postDelayed(timer, delayMs)
      return timer
    }
  }
}

interface ScheduledTimer {
  fun invalidate()
}
