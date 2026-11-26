// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import java.io.File
import java.util.Properties

/**
 * Persistent key-value storage, the counterpart of `UserDefaults.standard` on iOS. The values are
 * kept in a file in the user's application data directory.
 */
object UserDefaults {
  private var file: File? = null
  private val properties = Properties()

  /** Loads the stored values. Nothing is read or stored before this is called. */
  @Synchronized
  fun initialize() {
    val settingsFile = File(applicationDataDirectory(), "settings.properties")
    file = settingsFile
    if (settingsFile.exists()) {
      try {
        settingsFile.inputStream().use { properties.load(it) }
      } catch (e: Exception) {
        // Start from empty settings if the file cannot be read
      }
    }
  }

  @Synchronized fun string(key: String): String? = properties.getProperty(key)

  fun double(key: String): Double? = string(key)?.toDoubleOrNull()

  @Synchronized
  fun set(value: String, key: String) {
    if (file == null || properties.getProperty(key) == value) return
    properties.setProperty(key, value)
    save()
  }

  fun set(value: Double, key: String) {
    set(value.toString(), key)
  }

  private fun save() {
    val settingsFile = file ?: return
    try {
      settingsFile.parentFile.mkdirs()
      val temporaryFile = File(settingsFile.parentFile, settingsFile.name + ".tmp")
      temporaryFile.outputStream().use { properties.store(it, null) }
      if (!temporaryFile.renameTo(settingsFile)) {
        temporaryFile.copyTo(settingsFile, overwrite = true)
        temporaryFile.delete()
      }
    } catch (e: Exception) {
      // The settings are kept in memory if they cannot be written
    }
  }

  private fun applicationDataDirectory(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val base =
      if (os.contains("win")) {
        System.getenv("APPDATA")?.let { File(it) } ?: File(home)
      } else if (os.contains("mac")) {
        File(home, "Library/Application Support")
      } else {
        System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotEmpty() }?.let { File(it) }
          ?: File(home, ".config")
      }
    return File(base, "AdvantageTune")
  }
}
