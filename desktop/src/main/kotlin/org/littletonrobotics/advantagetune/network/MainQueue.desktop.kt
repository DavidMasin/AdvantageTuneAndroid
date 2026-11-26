// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.network

import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Serial queue that all NetworkTables state is confined to, the counterpart of
 * `DispatchQueue.main` and `Timer` on iOS.
 */
interface MainQueue {
  /** Runs the block after the work that is already queued. */
  fun async(block: () -> Unit)

  /** Runs the block once after the delay, or repeatedly at that interval. */
  fun schedule(intervalSeconds: Double, repeats: Boolean = false, block: () -> Unit): ScheduledTimer

  /** Queue backed by the event dispatch thread, which is also where the UI runs. */
  object Main : MainQueue {
    override fun async(block: () -> Unit) {
      SwingUtilities.invokeLater(block)
    }

    override fun schedule(
      intervalSeconds: Double,
      repeats: Boolean,
      block: () -> Unit,
    ): ScheduledTimer {
      val timer = Timer((intervalSeconds * 1000).toInt()) { block() }
      timer.isRepeats = repeats
      timer.start()
      return object : ScheduledTimer {
        override fun invalidate() {
          timer.stop()
        }
      }
    }
  }
}

interface ScheduledTimer {
  fun invalidate()
}
