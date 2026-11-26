// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.truncate
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.littletonrobotics.advantagetune.network.MessagePackStream

sealed class TopicValue {
  data class BooleanValue(val value: Boolean) : TopicValue()

  data class DoubleValue(val value: Double) : TopicValue()

  data class IntValue(val value: Long) : TopicValue()

  data class FloatValue(val value: Float) : TopicValue()

  data class StringValue(val value: String) : TopicValue()

  data class BooleanArrayValue(val value: List<Boolean>) : TopicValue()

  data class DoubleArrayValue(val value: List<Double>) : TopicValue()

  data class IntArrayValue(val value: List<Long>) : TopicValue()

  data class FloatArrayValue(val value: List<Float>) : TopicValue()

  data class StringArrayValue(val value: List<String>) : TopicValue()

  data class RawValue(val data: ByteString) : TopicValue()

  data class Struct(val typeName: String, val value: StructValue, val raw: ByteString) :
    TopicValue()

  data class ProtoRaw(val typeName: String, val data: ByteString) : TopicValue()

  data class Unsupported(val description: String) : TopicValue()

  final override fun toString(): String = formattedString

  val formattedString: String
    get() =
      when (this) {
        is BooleanValue -> if (value) "true" else "false"
        is DoubleValue -> value.cleanFormatted
        is IntValue -> NumberFormatting.format(value)
        is FloatValue -> value.cleanFormatted
        is StringValue ->
          if (value.characterCount > 60) {
            value.characterPrefix(57) + "…"
          } else {
            value
          }
        is BooleanArrayValue -> formatArray(value) { if (it) "true" else "false" }
        is DoubleArrayValue -> formatArray(value) { it.cleanFormatted }
        is IntArrayValue -> formatArray(value) { NumberFormatting.format(it) }
        is FloatArrayValue -> formatArray(value) { it.cleanFormatted }
        is StringArrayValue -> formatArray(value) { "\"$it\"" }
        is Struct -> value.formattedSummary
        is ProtoRaw -> "$typeName (${Localized.bytes(data.size)})"
        is RawValue -> Localized.bytes(data.size)
        is Unsupported -> description
      }

  /**
   * Complete string representation without truncation or floating point noise (suitable for
   * copying and exporting).
   */
  val fullString: String
    get() =
      when (this) {
        is BooleanValue -> if (value) "true" else "false"
        is DoubleValue -> value.cleanFormatted
        is IntValue -> "$value"
        is FloatValue -> value.cleanFormatted
        is StringValue -> value
        is BooleanArrayValue -> "[" + value.joinToString(", ") { if (it) "true" else "false" } + "]"
        is DoubleArrayValue -> "[" + value.joinToString(", ") { it.cleanFormatted } + "]"
        is IntArrayValue -> "[" + value.joinToString(", ") { "$it" } + "]"
        is FloatArrayValue -> "[" + value.joinToString(", ") { it.cleanFormatted } + "]"
        is StringArrayValue -> "[" + value.joinToString(", ") { "\"$it\"" } + "]"
        is Struct -> value.fullString
        is ProtoRaw -> data.hexString
        is RawValue -> data.hexString
        is Unsupported -> description
      }

  val typeDisplayName: String
    get() =
      when (this) {
        is BooleanValue -> "Boolean"
        is DoubleValue -> "Double"
        is IntValue -> "Integer"
        is FloatValue -> "Float"
        is StringValue -> "String"
        is BooleanArrayValue -> "Boolean[]"
        is DoubleArrayValue -> "Double[]"
        is IntArrayValue -> "Integer[]"
        is FloatArrayValue -> "Float[]"
        is StringArrayValue -> "String[]"
        is RawValue -> "Raw Data"
        is Struct -> typeName
        is ProtoRaw -> typeName
        is Unsupported -> description
      }

  val isPrimitive: Boolean
    get() = this is DoubleValue || this is IntValue || this is FloatValue || this is BooleanValue

  val isArray: Boolean
    get() =
      this is BooleanArrayValue || this is DoubleArrayValue || this is IntArrayValue ||
        this is FloatArrayValue || this is StringArrayValue

  val isRaw: Boolean
    get() = this is RawValue || this is ProtoRaw

  val asDouble: Double?
    get() =
      when (this) {
        is DoubleValue -> value
        is IntValue -> value.toDouble()
        is FloatValue -> value.toDouble()
        is BooleanValue -> if (value) 1.0 else 0.0
        is StringValue -> value.toDoubleOrNull()
        else -> null
      }

  fun cast(targetType: String): TopicValue? {
    return when (targetType.lowercase()) {
      "boolean",
      "bool" -> BooleanValue(asDouble?.let { it != 0.0 } ?: false)
      "double" -> DoubleValue(asDouble ?: 0.0)
      "int",
      "int64" -> IntValue((asDouble ?: 0.0).toLong())
      "float",
      "float32" -> FloatValue((asDouble ?: 0.0).toFloat())
      "string" -> StringValue(formattedString)
      "double[]" -> if (this is DoubleArrayValue) DoubleArrayValue(value) else DoubleArrayValue(listOf())
      else -> this
    }
  }

  val structValueRepresentation: StructValue
    get() =
      when (this) {
        is BooleanValue -> StructValue.BoolValue(value)
        is IntValue -> StructValue.IntValue(value)
        is DoubleValue -> StructValue.DoubleValue(value)
        is FloatValue -> StructValue.FloatValue(value)
        is StringValue -> StructValue.StringValue(value)
        is BooleanArrayValue -> StructValue.ArrayValue(value.map { StructValue.BoolValue(it) })
        is DoubleArrayValue -> StructValue.ArrayValue(value.map { StructValue.DoubleValue(it) })
        is IntArrayValue -> StructValue.ArrayValue(value.map { StructValue.IntValue(it) })
        is FloatArrayValue -> StructValue.ArrayValue(value.map { StructValue.FloatValue(it) })
        is StringArrayValue -> StructValue.ArrayValue(value.map { StructValue.StringValue(it) })
        is Struct -> value
        is ProtoRaw -> StructValue.StringValue("Protobuf (${Localized.bytes(data.size)})")
        is RawValue -> StructValue.StringValue(Localized.bytes(data.size))
        is Unsupported -> StructValue.StringValue(description)
      }

  // MARK: - MessagePack Stream Conversions

  fun toMessagePackStreamValue(): MessagePackStream.Value {
    return when (this) {
      is BooleanValue -> MessagePackStream.Value.BoolValue(value)
      is DoubleValue -> MessagePackStream.Value.DoubleValue(value)
      is IntValue -> MessagePackStream.Value.IntValue(value)
      is FloatValue -> MessagePackStream.Value.FloatValue(value)
      is StringValue -> MessagePackStream.Value.StringValue(value)
      is BooleanArrayValue ->
        MessagePackStream.Value.ArrayValue(value.map { MessagePackStream.Value.BoolValue(it) })
      is DoubleArrayValue ->
        MessagePackStream.Value.ArrayValue(value.map { MessagePackStream.Value.DoubleValue(it) })
      is IntArrayValue ->
        MessagePackStream.Value.ArrayValue(value.map { MessagePackStream.Value.IntValue(it) })
      is FloatArrayValue ->
        MessagePackStream.Value.ArrayValue(value.map { MessagePackStream.Value.FloatValue(it) })
      is StringArrayValue ->
        MessagePackStream.Value.ArrayValue(value.map { MessagePackStream.Value.StringValue(it) })
      is Struct -> MessagePackStream.Value.BinaryValue(raw)
      is ProtoRaw -> MessagePackStream.Value.BinaryValue(data)
      is RawValue -> MessagePackStream.Value.BinaryValue(data)
      is Unsupported -> MessagePackStream.Value.StringValue(description)
    }
  }

  companion object {
    fun from(typeIndex: Int, value: MessagePackStream.Value): TopicValue {
      return when (typeIndex) {
        0 -> BooleanValue(value.asBool ?: false) // boolean
        1 -> DoubleValue(value.asDouble ?: 0.0) // double
        2 -> IntValue(value.asInt64 ?: 0) // int
        3 -> FloatValue((value.asDouble ?: 0.0).toFloat()) // float

        4 -> // string / json
          if (value is MessagePackStream.Value.StringValue) {
            StringValue(value.value)
          } else {
            StringValue("$value")
          }

        5 -> // raw / binary
          when (value) {
            is MessagePackStream.Value.BinaryValue -> RawValue(value.value)
            is MessagePackStream.Value.StringValue -> RawValue(value.value.encodeUtf8())
            else -> Unsupported("Raw data")
          }

        16 -> // boolean[]
          if (value is MessagePackStream.Value.ArrayValue) {
            BooleanArrayValue(value.elements.map { it.asBool ?: false })
          } else {
            BooleanArrayValue(listOf())
          }

        17 -> // double[]
          if (value is MessagePackStream.Value.ArrayValue) {
            DoubleArrayValue(value.elements.mapNotNull { it.asDouble })
          } else {
            DoubleArrayValue(listOf())
          }

        18 -> // int[]
          if (value is MessagePackStream.Value.ArrayValue) {
            IntArrayValue(value.elements.mapNotNull { it.asInt64 })
          } else {
            IntArrayValue(listOf())
          }

        19 -> // float[]
          if (value is MessagePackStream.Value.ArrayValue) {
            FloatArrayValue(value.elements.mapNotNull { it.asDouble?.toFloat() })
          } else {
            FloatArrayValue(listOf())
          }

        20 -> // string[]
          if (value is MessagePackStream.Value.ArrayValue) {
            StringArrayValue(value.elements.map { it.asString ?: "$it" })
          } else {
            StringArrayValue(listOf())
          }

        else -> Unsupported("Type Index $typeIndex")
      }
    }

    // MARK: - Formatting Helpers

    private fun <T> formatArray(
      arr: List<T>,
      maxCharacters: Int = 200,
      transform: (T) -> String,
    ): String {
      val prefixArr = arr.take(maxCharacters)
      val full =
        "[" + prefixArr.joinToString(", ", transform = transform) +
          (if (arr.size > prefixArr.size) ", …" else "") + "]"
      if (full.characterCount > maxCharacters) {
        return full.characterPrefix(maxCharacters - 3) + "…"
      }
      return full
    }
  }
}

// MARK: - Double & Float Clean Formatting

/** Formats the double cleanly without IEEE-754 epsilon noise or awkward "-0" signs. */
val Double.cleanFormatted: String
  get() {
    if (this == 0.0 || (this > -1e-12 && this < 1e-12)) return "0"
    if (this.isNaN()) return "NaN"
    if (this.isInfinite()) return if (this > 0) "Infinity" else "-Infinity"

    val rounded = (this * 1e12).roundedAwayFromZero() / 1e12
    val value = if (abs(this - rounded) < 1e-13) rounded else this
    val s = NumberFormatting.format(value)
    if (s == "-0" || s == "-0.0" || s == "-0,0") return "0"
    return s
  }

/** Formats the float cleanly without IEEE-754 epsilon noise or awkward "-0" signs. */
val Float.cleanFormatted: String
  get() {
    if (this == 0.0f || (this > -1e-6 && this < 1e-6)) return "0"
    if (this.isNaN()) return "NaN"
    if (this.isInfinite()) return if (this > 0) "Infinity" else "-Infinity"

    val d = this.toDouble()
    val rounded = (d * 1e6).roundedAwayFromZero() / 1e6
    val value = if (abs(d - rounded) < 1e-7) rounded else d
    val s = NumberFormatting.format(value)
    if (s == "-0" || s == "-0.0" || s == "-0,0") return "0"
    return s
  }

/** Rounds to the nearest integral value, with ties rounding away from zero. */
fun Double.roundedAwayFromZero(): Double {
  if (isNaN() || isInfinite()) return this
  val truncated = truncate(this)
  return if (abs(this - truncated) >= 0.5) truncated + sign(this) else truncated
}

/** Locale-aware number formatting and parsing without grouping separators. */
object NumberFormatting {
  private var cachedLocale: Locale? = null
  private var cachedFormat: NumberFormat? = null

  private fun numberFormat(): NumberFormat {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    cachedFormat?.let { if (cachedLocale == locale) return it }

    val format = NumberFormat.getNumberInstance(locale)
    format.isGroupingUsed = false
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 6
    format.roundingMode = RoundingMode.HALF_EVEN
    cachedLocale = locale
    cachedFormat = format
    return format
  }

  @Synchronized fun format(value: Double): String = numberFormat().format(value)

  @Synchronized fun format(value: Long): String = numberFormat().format(value)

  /** Formats with a fixed number of fraction digits. */
  @Synchronized
  fun format(value: Double, fractionLength: Int, grouping: Boolean = false): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault(Locale.Category.FORMAT))
    format.isGroupingUsed = grouping
    format.minimumFractionDigits = fractionLength
    format.maximumFractionDigits = fractionLength
    format.roundingMode = RoundingMode.HALF_EVEN
    return format.format(value)
  }

  @Synchronized
  fun parseDouble(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return parse(trimmed)?.toDouble() ?: trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }
  }

  @Synchronized
  fun parseLong(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parsed = parse(trimmed, integerOnly = true)
    if (parsed is Long) return parsed
    return trimmed.toLongOrNull()
  }

  private fun parse(text: String, integerOnly: Boolean = false): Number? {
    val format = numberFormat().clone() as NumberFormat
    format.isParseIntegerOnly = integerOnly
    (format as? DecimalFormat)?.isParseBigDecimal = false
    val position = ParsePosition(0)
    val number = format.parse(text, position) ?: return null
    if (position.index != text.length || position.errorIndex != -1) return null
    return number
  }
}

// MARK: - String Helpers

/** Number of Unicode characters (code points) in the string. */
val String.characterCount: Int
  get() = codePointCount(0, length)

/** The first `count` Unicode characters (code points) of the string. */
fun String.characterPrefix(count: Int): String {
  if (characterCount <= count) return this
  return substring(0, offsetByCodePoints(0, count))
}

/** Uppercase hex bytes separated by spaces (e.g. "01 AB FF"). */
val ByteString.hexString: String
  get() = hexString(size)

/** Uppercase hex of the first `count` bytes separated by spaces. */
fun ByteString.hexString(count: Int): String {
  val digits = "0123456789ABCDEF"
  val length = minOf(count, size)
  if (length <= 0) return ""
  val builder = StringBuilder(length * 3 - 1)
  for (i in 0 until length) {
    if (i > 0) builder.append(' ')
    val byte = this[i].toInt() and 0xFF
    builder.append(digits[byte ushr 4]).append(digits[byte and 0x0F])
  }
  return builder.toString()
}
