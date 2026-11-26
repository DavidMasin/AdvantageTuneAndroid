// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

object UserDefaultsKeys {
  const val serverHost = "server_host"
  const val defaultStepSize = "default_step_size"

  fun stepSize(topicPath: String): String = "step_size_$topicPath"
}
