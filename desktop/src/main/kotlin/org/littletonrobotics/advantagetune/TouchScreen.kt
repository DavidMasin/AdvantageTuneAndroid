// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.math.abs

/** Finds the display that is a touch screen, so the window can open there. */
object TouchScreen {
  private const val POINTER_DEVICE_TYPE_TOUCH = 3

  /** The part of the Windows pointer device API that is used here. */
  private interface PointerDevices : StdCallLibrary {
    fun GetPointerDevices(deviceCount: IntByReference, pointerDevices: Pointer?): Boolean
  }

  /** `POINTER_DEVICE_INFO` of the Windows API. */
  @Structure.FieldOrder(
    "displayOrientation",
    "device",
    "pointerDeviceType",
    "monitor",
    "startingCursorId",
    "maxActiveContacts",
    "productString",
  )
  class PointerDeviceInfo(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var displayOrientation: Int = 0
    @JvmField var device: Pointer? = null
    @JvmField var pointerDeviceType: Int = 0
    @JvmField var monitor: Pointer? = null
    @JvmField var startingCursorId: Int = 0
    @JvmField var maxActiveContacts: Short = 0
    @JvmField var productString: CharArray = CharArray(520)
  }

  /** Whether the computer has a touch screen. */
  val isPresent: Boolean by lazy {
    if (!isWindows) {
      false
    } else {
      try {
        touchMonitorBounds() != null
      } catch (e: Throwable) {
        false
      }
    }
  }

  private val isWindows: Boolean
    get() = System.getProperty("os.name").lowercase().contains("win")

  /**
   * Returns the bounds of the first touch screen in the coordinates that windows are placed in,
   * or null if there is none or it cannot be determined. Only Windows is supported.
   */
  fun findBounds(): Rectangle? {
    if (!isWindows) return null
    return try {
      val monitor = touchMonitorBounds() ?: return null
      screenMatching(monitor)
    } catch (e: Throwable) {
      null
    }
  }

  /** Returns the bounds in pixels of the monitor of the first touch device. Only for Windows. */
  internal fun touchMonitorBounds(): Rectangle? {
    val library =
      Native.load("user32", PointerDevices::class.java, W32APIOptions.DEFAULT_OPTIONS)

    val count = IntByReference(0)
    if (!library.GetPointerDevices(count, null) || count.value <= 0) return null

    val first = PointerDeviceInfo()
    @Suppress("UNCHECKED_CAST")
    val devices = first.toArray(count.value) as Array<PointerDeviceInfo>
    if (!library.GetPointerDevices(count, first.pointer)) return null

    for (index in 0 until minOf(count.value, devices.size)) {
      val device = devices[index]
      device.read()
      val monitor = device.monitor
      if (device.pointerDeviceType != POINTER_DEVICE_TYPE_TOUCH || monitor == null) continue

      val info = WinUser.MONITORINFO()
      if (User32.INSTANCE.GetMonitorInfo(WinUser.HMONITOR(monitor), info).booleanValue()) {
        val area = info.rcMonitor
        return Rectangle(area.left, area.top, area.right - area.left, area.bottom - area.top)
      }
    }
    return null
  }

  /** Returns the bounds of the screen whose pixels are the given monitor's. */
  private fun screenMatching(monitor: Rectangle): Rectangle? {
    for (device in GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices) {
      val configuration = device.defaultConfiguration
      val bounds = configuration.bounds
      val scaleX = configuration.defaultTransform.scaleX
      val scaleY = configuration.defaultTransform.scaleY

      // Windows reports the monitor in pixels and Java reports the screen scaled for its density
      val matchesPosition =
        abs(bounds.x * scaleX - monitor.x) <= 2 && abs(bounds.y * scaleY - monitor.y) <= 2
      val matchesSize =
        abs(bounds.width * scaleX - monitor.width) <= 2 &&
          abs(bounds.height * scaleY - monitor.height) <= 2
      if (matchesSize && (matchesPosition || bounds.location == monitor.location)) {
        return bounds
      }
    }
    return null
  }
}
