// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

/** Recursive representation of structured data (e.g. WPILib Structs). */
sealed class StructValue {
  data class BoolValue(val value: Boolean) : StructValue()

  data class IntValue(val value: Long) : StructValue()

  data class UIntValue(val value: ULong) : StructValue()

  data class FloatValue(val value: Float) : StructValue()

  data class DoubleValue(val value: Double) : StructValue()

  data class StringValue(val value: String) : StructValue()

  data class EnumValue(
    val name: String,
    val rawValue: Long,
    val options: Map<Long, String> = emptyMap(),
  ) : StructValue()

  data class ArrayValue(val elements: List<StructValue>) : StructValue()

  data class ObjectValue(val fields: List<Pair<String, StructValue>>) : StructValue()

  // MARK: - String Formatting

  private val unclippedSummary: String
    get() =
      when (this) {
        is BoolValue -> if (value) "true" else "false"
        is IntValue -> "$value"
        is UIntValue -> "$value"
        is FloatValue -> value.cleanFormatted
        is DoubleValue -> value.cleanFormatted
        is StringValue -> "\"$value\""
        is EnumValue -> name
        is ArrayValue -> "[" + elements.joinToString(", ") { it.unclippedSummary } + "]"
        is ObjectValue ->
          "{" + fields.joinToString(", ") { "${it.first}: ${it.second.unclippedSummary}" } + "}"
      }

  /** Summary for previewing in rows and details, capped by total character length. */
  val formattedSummary: String
    get() {
      val summary = unclippedSummary
      if (summary.characterCount > 200) {
        return summary.characterPrefix(197) + "…"
      }
      return summary
    }

  /**
   * Complete string representation without truncation or floating point noise (suitable for
   * copying and exporting).
   */
  val fullString: String
    get() =
      when (this) {
        is BoolValue -> if (value) "true" else "false"
        is IntValue -> "$value"
        is UIntValue -> "$value"
        is FloatValue -> value.cleanFormatted
        is DoubleValue -> value.cleanFormatted
        is StringValue -> "\"$value\""
        is EnumValue -> "$name ($rawValue)"
        is ArrayValue -> "[" + elements.joinToString(", ") { it.fullString } + "]"
        is ObjectValue ->
          "{" + fields.joinToString(", ") { "\"${it.first}\": ${it.second.fullString}" } + "}"
      }
}
