// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import org.littletonrobotics.advantagetune.network.MainQueue
import org.littletonrobotics.advantagetune.network.ScheduledTimer

/** Queue for tests that runs work when [runUntilIdle] is called. */
class TestQueue : MainQueue {
  private val pending = ArrayDeque<() -> Unit>()

  override fun async(block: () -> Unit) {
    pending.addLast(block)
  }

  override fun schedule(intervalSeconds: Double, repeats: Boolean, block: () -> Unit): ScheduledTimer {
    var isValid = true
    pending.addLast { if (isValid) block() }
    return object : ScheduledTimer {
      override fun invalidate() {
        isValid = false
      }
    }
  }

  fun runUntilIdle() {
    while (pending.isNotEmpty()) {
      pending.removeFirst().invoke()
    }
  }
}
