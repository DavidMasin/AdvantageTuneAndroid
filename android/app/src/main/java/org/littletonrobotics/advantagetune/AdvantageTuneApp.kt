// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.UserDefaults
import org.littletonrobotics.advantagetune.views.components.AdvantageTuneTheme
import org.littletonrobotics.advantagetune.views.components.Haptics

class AdvantageTuneApp : Application() {
  override fun onCreate() {
    super.onCreate()
    UserDefaults.initialize(this)
    Localized.initialize(this)
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    Haptics.attach(window.decorView)

    setContent { AdvantageTuneTheme { ContentView() } }
  }
}
