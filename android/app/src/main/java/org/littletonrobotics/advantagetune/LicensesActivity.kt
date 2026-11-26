// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.littletonrobotics.advantagetune.views.components.AdvantageTuneTheme
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.InlineTitleBar
import org.littletonrobotics.advantagetune.views.components.InsetGroupedList
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.NavigationStack
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.section

/** Joins the lines of each paragraph so the text wraps to the width of the screen. */
private fun reflow(text: String): String {
  return text.trim().replace(Regex("(?<!\\n)\\n(?!\\n)(?![ \\t]*[-*] )[ \\t]*"), " ")
}

private class License(val name: String, val notice: String?, @RawRes val text: Int)

private val licenses =
  listOf(
    License("AdvantageTune", null, R.raw.license_advantagetune),
    License(
      "Android Jetpack",
      "Copyright The Android Open Source Project",
      R.raw.license_apache,
    ),
    License(
      "Kotlin",
      "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
      R.raw.license_apache,
    ),
    License("OkHttp", "Copyright Square, Inc.", R.raw.license_apache),
    License("Protocol Buffers", null, R.raw.license_protobuf),
  )

/**
 * Shows the licenses of AdvantageTune and the libraries it uses. Like the licenses pane in the
 * Settings app on iOS, it is opened from the app's page in the system settings.
 */
class LicensesActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent { AdvantageTuneTheme { LicensesView(onClose = { finish() }) } }
  }
}

@Composable
private fun LicensesView(onClose: () -> Unit) {
  val navigator = remember { Navigator<License>() }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(AppTheme.colors.systemGroupedBackground)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        )
  ) {
    NavigationStack(
      navigator = navigator,
      root = {
        Column(
          modifier = Modifier.fillMaxSize().background(AppTheme.colors.systemGroupedBackground)
        ) {
          val title = stringResource(R.string.licenses)
          InlineTitleBar(title = title, onBack = onClose)

          InsetGroupedList {
            section(key = "licenses", header = title) {
              for (license in licenses) {
                row(key = license.name) { position ->
                  ListRow(
                    position = position,
                    showsChevron = true,
                    onClick = { navigator.push(license) },
                  ) {
                    Text(text = license.name, style = AppType.body, color = AppTheme.colors.label)
                  }
                }
              }
            }
          }
        }
      },
    ) { license ->
      val resources = LocalResources.current
      val text =
        remember(license, resources) {
          val body = resources.openRawResource(license.text).bufferedReader().readText()
          reflow(if (license.notice != null) "${license.notice}\n\n$body" else body)
        }

      Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.systemGroupedBackground)) {
        InlineTitleBar(title = license.name, onBack = { navigator.pop() })

        InsetGroupedList {
          section(key = "license") {
            row { position ->
              ListRow(position = position) {
                Text(
                  text = text,
                  style = AppType.footnote,
                  color = AppTheme.colors.secondaryLabel,
                )
              }
            }
          }
        }
      }
    }
  }
}
