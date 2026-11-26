// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/** The system colors used by the iOS app, so both platforms share one palette. */
@Immutable
class AppColors(
  val accent: Color,
  val label: Color,
  val secondaryLabel: Color,
  val tertiaryLabel: Color,
  val systemBackground: Color,
  val secondarySystemBackground: Color,
  val systemGroupedBackground: Color,
  val secondarySystemGroupedBackground: Color,
  val tertiarySystemFill: Color,
  val systemGray5: Color,
  val separator: Color,
  val barBackground: Color,
  val menuBackground: Color,
  val segmentedThumb: Color,
  val red: Color,
  val green: Color,
  val yellow: Color,
  val orange: Color,
  val blue: Color,
  val indigo: Color,
  val purple: Color,
  val teal: Color,
  val gray: Color,
)

private val AccentColor = Color(0xFF446CE3)

private val LightAppColors =
  AppColors(
    accent = AccentColor,
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    systemBackground = Color(0xFFFFFFFF),
    secondarySystemBackground = Color(0xFFF2F2F7),
    systemGroupedBackground = Color(0xFFF2F2F7),
    secondarySystemGroupedBackground = Color(0xFFFFFFFF),
    tertiarySystemFill = Color(0x1F767680),
    systemGray5 = Color(0xFFE5E5EA),
    separator = Color(0x4A3C3C43),
    barBackground = Color(0xFFF9F9F9),
    menuBackground = Color(0xFFFFFFFF),
    segmentedThumb = Color(0xFFFFFFFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    yellow = Color(0xFFFFCC00),
    orange = Color(0xFFFF9500),
    blue = Color(0xFF007AFF),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    teal = Color(0xFF30B0C7),
    gray = Color(0xFF8E8E93),
  )

private val DarkAppColors =
  AppColors(
    accent = AccentColor,
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    systemBackground = Color(0xFF000000),
    secondarySystemBackground = Color(0xFF1C1C1E),
    systemGroupedBackground = Color(0xFF000000),
    secondarySystemGroupedBackground = Color(0xFF1C1C1E),
    tertiarySystemFill = Color(0x3D767680),
    systemGray5 = Color(0xFF2C2C2E),
    separator = Color(0x99545458),
    barBackground = Color(0xFF161618),
    menuBackground = Color(0xFF2C2C2E),
    segmentedThumb = Color(0xFF636366),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    yellow = Color(0xFFFFD60A),
    orange = Color(0xFFFF9F0A),
    blue = Color(0xFF0A84FF),
    indigo = Color(0xFF5E5CE6),
    purple = Color(0xFFBF5AF2),
    teal = Color(0xFF40C8E0),
    gray = Color(0xFF8E8E93),
  )

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
  val colors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/**
 * The text styles used by the iOS app at the default text size. Text runs in the direction of its
 * content (so topic names and numbers read left to right in every language), as on iOS.
 */
object AppType {
  val largeTitle = style(size = 34, lineHeight = 41, weight = FontWeight.Bold)
  val title2 = style(size = 22, lineHeight = 28)
  val headline = style(size = 17, lineHeight = 22, weight = FontWeight.SemiBold)
  val body = style(size = 17, lineHeight = 22)
  val subheadline = style(size = 15, lineHeight = 20)
  val footnote = style(size = 13, lineHeight = 18)
  val caption = style(size = 12, lineHeight = 16)
  val caption2 = style(size = 11, lineHeight = 13)

  /** A style for small labels of the given size. */
  fun system(size: Int, weight: FontWeight): TextStyle {
    return TextStyle(
      fontSize = size.sp,
      fontWeight = weight,
      textDirection = TextDirection.ContentOrLtr,
    )
  }

  private fun style(size: Int, lineHeight: Int, weight: FontWeight? = null): TextStyle {
    return TextStyle(
      fontSize = size.sp,
      lineHeight = lineHeight.sp,
      fontWeight = weight,
      textDirection = TextDirection.ContentOrLtr,
    )
  }

  /** Aligns text at the leading edge of the layout, whatever the direction of the text. */
  val leadingAlignment: TextAlign
    @Composable
    @ReadOnlyComposable
    get() =
      if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left

  /** Aligns text at the trailing edge of the layout, whatever the direction of the text. */
  val trailingAlignment: TextAlign
    @Composable
    @ReadOnlyComposable
    get() =
      if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Left else TextAlign.Right
}

fun TextStyle.weight(weight: FontWeight): TextStyle = copy(fontWeight = weight)

fun TextStyle.monospaced(): TextStyle = copy(fontFamily = FontFamily.Monospace)

@Composable
fun AdvantageTuneTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colors = if (darkTheme) DarkAppColors else LightAppColors

  val colorScheme =
    if (darkTheme) {
      darkColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        secondary = colors.accent,
        onSecondary = Color.White,
        background = colors.systemBackground,
        onBackground = colors.label,
        surface = colors.systemBackground,
        onSurface = colors.label,
        onSurfaceVariant = colors.gray,
        surfaceContainer = colors.menuBackground,
        error = colors.red,
        outline = colors.separator,
        outlineVariant = colors.separator,
      )
    } else {
      lightColorScheme(
        primary = colors.accent,
        onPrimary = Color.White,
        secondary = colors.accent,
        onSecondary = Color.White,
        background = colors.systemBackground,
        onBackground = colors.label,
        surface = colors.systemBackground,
        onSurface = colors.label,
        onSurfaceVariant = colors.gray,
        surfaceContainer = colors.menuBackground,
        error = colors.red,
        outline = colors.separator,
        outlineVariant = colors.separator,
      )
    }

  CompositionLocalProvider(LocalAppColors provides colors) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}
