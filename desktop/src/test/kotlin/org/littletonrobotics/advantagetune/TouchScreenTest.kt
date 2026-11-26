// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class TouchScreenTest {
  private val isWindows = System.getProperty("os.name").lowercase().contains("win")

  @Test
  fun queriesPointerDevicesOnWindows() {
    assumeTrue(isWindows)
    // Machines that run the tests rarely have a touch screen, so this checks that Windows accepts
    // the query, not what it answers
    TouchScreen.touchMonitorBounds()
  }

  @Test
  fun findsNothingOnOtherSystems() {
    assumeFalse(isWindows)
    assertNull(TouchScreen.findBounds())
  }
}
