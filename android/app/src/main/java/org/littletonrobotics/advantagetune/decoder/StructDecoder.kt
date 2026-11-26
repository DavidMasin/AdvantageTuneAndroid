// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.decoder

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.network.decodeUtf8OrNull

/**
 * Engine to compile WPILib Struct schemas and decode binary struct payloads.
 *
 * Specification: https://github.com/wpilibsuite/allwpilib/blob/main/wpiutil/doc/struct.adoc
 */
class StructDecoder {
  private val schemaStrings: MutableMap<String, String> = LinkedHashMap()
  private val schemas: MutableMap<String, Schema> = HashMap()

  // MARK: - Schema Management

  /** Registers a schema buffer announced under `/.schema/struct:<TypeName>`. */
  @Synchronized
  fun addSchema(name: String, schemaData: ByteString) {
    val schemaStr = schemaData.decodeUtf8OrNull() ?: return

    if (schemaStrings[name] != null) return
    schemaStrings[name] = schemaStr

    // Iteratively compile schemas in dependency order
    while (true) {
      var compileSuccess = false
      for ((schemaName, str) in schemaStrings) {
        if (schemas[schemaName] == null) {
          val success = compileSchema(name = schemaName, schema = str)
          if (success) {
            compileSuccess = true
          }
        }
      }
      if (!compileSuccess) {
        break
      }
    }
  }

  /** Checks if a struct schema is registered and compiled. */
  @Synchronized
  fun hasSchema(typeName: String): Boolean {
    val clean = cleanTypeName(typeName)
    return schemas[clean] != null
  }

  fun cleanTypeName(typeName: String): String {
    var clean = typeName
    if (clean.startsWith("struct:")) {
      clean = clean.substring("struct:".length)
    }
    if (clean.endsWith("[]")) {
      clean = clean.substring(0, clean.length - 2)
    }
    return clean
  }

  // MARK: - Schema Compilation

  private fun compileSchema(name: String, schema: String): Boolean {
    val valueSchemaStrs = schema.trim().split(";").map { it.trim() }.filter { it.isNotEmpty() }

    val valueSchemas = mutableListOf<ValueSchema>()

    for (rawStr in valueSchemaStrs) {
      var schemaStr = rawStr
      var enumData: Map<Long, String>? = null

      // Check for enum declaration: enum {A = 0, B = 1} int8 fieldName or enum Name { ... }
      if (schemaStr.startsWith("enum")) {
        val startIdx = schemaStr.indexOf('{')
        val endIdx = schemaStr.indexOf('}')
        if (startIdx >= 0 && endIdx > startIdx) {
          val enumBody = schemaStr.substring(startIdx + 1, endIdx)
          val dict = LinkedHashMap<Long, String>()
          val pairs = enumBody.split(",")
          for (pair in pairs) {
            val trimmedPair = pair.trim()
            if (trimmedPair.isEmpty()) continue
            val parts = trimmedPair.split("=").filter { it.isNotEmpty() }.map { it.trim() }
            if (parts.size == 2) {
              val rawValStr = parts[1]
              val rawVal: Long? =
                if (rawValStr.startsWith("0x") || rawValStr.startsWith("0X")) {
                  rawValStr.substring(2).toLongOrNull(radix = 16)
                } else {
                  rawValStr.toLongOrNull()
                }
              if (rawVal != null) {
                dict[rawVal] = parts[0]
              }
            }
          }
          enumData = if (dict.isEmpty()) null else dict
          schemaStr = schemaStr.substring(endIdx + 1).trim()
        }
      }

      val tokens = schemaStr.split(" ").filter { it.isNotEmpty() }.toMutableList()
      if (tokens.isEmpty()) continue

      var typeStr = tokens.removeAt(0)
      if (typeStr.startsWith("struct:")) {
        typeStr = typeStr.substring("struct:".length)
      }

      // If custom type and not compiled yet, fail dependency
      if (ValueType.from(typeStr) == null && schemas[typeStr] == null) {
        return false
      }

      val nameStr = tokens.joinToString("")
      val fieldName: String
      var bitfieldWidth: Int? = null
      var arrayLength: Int? = null

      if (nameStr.contains(":")) {
        val parts = nameStr.split(":").filter { it.isNotEmpty() }
        fieldName = parts.getOrElse(0) { "" }
        if (parts.size > 1) {
          parts[1].toIntOrNull()?.let { bitfieldWidth = it }
        }
      } else if (nameStr.contains("[")) {
        val parts = nameStr.split("[").filter { it.isNotEmpty() }
        fieldName = parts.getOrElse(0) { "" }
        if (parts.size > 1) {
          val lenStr = parts[1].replace("]", "").trim(' ', '\t')
          arrayLength = lenStr.toIntOrNull()
        }
      } else {
        fieldName = nameStr
      }

      valueSchemas.add(
        ValueSchema(
          name = fieldName,
          type = typeStr,
          enumData = enumData,
          bitfieldWidth = bitfieldWidth,
          arrayLength = arrayLength,
          bitRange = BitRange(0, 0),
        )
      )
    }

    // Calculate bit positions
    var bitPosition = 0
    var bitfieldPosition: Int? = null
    var bitfieldLength: Int? = null

    for (i in valueSchemas.indices) {
      val valSchema = valueSchemas[i]

      val primitiveType = ValueType.from(valSchema.type)
      val childSchema = schemas[valSchema.type]
      if (primitiveType != null) {
        val typeLength = primitiveType.maxBits

        val bfWidth = valSchema.bitfieldWidth
        if (bfWidth != null) {
          val valueBitLength = min(bfWidth, typeLength)
          var position = bitfieldPosition
          var length = bitfieldLength
          if (
            position == null || length == null ||
              (primitiveType != ValueType.BOOL && length != typeLength) ||
              (position + valueBitLength > length)
          ) {
            if (position != null && length != null) {
              bitPosition += length - position
            }
            position = 0
            length = typeLength
          }

          valueSchemas[i].bitRange = BitRange(bitPosition, bitPosition + valueBitLength)
          bitfieldPosition = position + valueBitLength
          bitfieldLength = length
          bitPosition += valueBitLength
        } else {
          val position = bitfieldPosition
          val length = bitfieldLength
          if (position != null && length != null) {
            bitPosition += length - position
          }
          bitfieldPosition = null
          bitfieldLength = null

          var bitLength = typeLength
          valSchema.arrayLength?.let { bitLength *= it }
          valueSchemas[i].bitRange = BitRange(bitPosition, bitPosition + bitLength)
          bitPosition += bitLength
        }
      } else if (childSchema != null) {
        // Child struct
        val position = bitfieldPosition
        val bfLength = bitfieldLength
        if (position != null && bfLength != null) {
          bitPosition += bfLength - position
        }
        bitfieldPosition = null
        bitfieldLength = null

        var length = childSchema.length
        valSchema.arrayLength?.let { length *= it }
        valueSchemas[i].bitRange = BitRange(bitPosition, bitPosition + length)
        bitPosition += length
      }
    }

    val position = bitfieldPosition
    val length = bitfieldLength
    if (position != null && length != null) {
      bitPosition += length - position
    }

    schemas[name] = Schema(length = bitPosition, valueSchemas = valueSchemas)
    return true
  }

  // MARK: - Decoding

  /** Decodes a struct binary buffer given its schema name. */
  @Synchronized
  fun decode(typeName: String, data: ByteString): StructValue? {
    val clean = cleanTypeName(typeName)
    val isArray = typeName.endsWith("[]")

    return if (isArray) {
      decodeArrayInternal(name = clean, data = data.toByteArray())
    } else {
      decodeInternal(name = clean, data = data.toByteArray())
    }
  }

  // MARK: - Encoding / Binary Packing

  /**
   * Encodes a StructValue back into a WPILib struct binary buffer according to the registered
   * schema.
   */
  @Synchronized
  fun encode(typeName: String, value: StructValue): ByteString? {
    val clean = cleanTypeName(typeName)
    val isArray = typeName.endsWith("[]")

    if (isArray) {
      if (value !is StructValue.ArrayValue) return null
      val result = okio.Buffer()
      for (element in value.elements) {
        val itemData = encodeInternal(name = clean, value = element) ?: return null
        result.write(itemData)
      }
      return result.readByteString()
    } else {
      return encodeInternal(name = clean, value = value)?.toByteString()
    }
  }

  private fun encodeInternal(name: String, value: StructValue): ByteArray? {
    val schema = schemas[name] ?: return null
    if (value !is StructValue.ObjectValue) return null

    val fieldMap = value.fields.toMap()
    val bits = BooleanArray(schema.length)

    for (valueSchema in schema.valueSchemas) {
      val fieldVal = fieldMap[valueSchema.name]
      val range = valueSchema.bitRange
      val bitLen = range.end - range.start

      val primitiveType = ValueType.from(valueSchema.type)
      val childSchema = schemas[valueSchema.type]
      if (primitiveType != null) {
        val arrayLength = valueSchema.arrayLength
        if (arrayLength != null) {
          val elements: List<StructValue> =
            if (fieldVal is StructValue.ArrayValue) {
              fieldVal.elements
            } else if (fieldVal is StructValue.StringValue && primitiveType == ValueType.CHAR) {
              fieldVal.value.codePoints().toArray().map {
                StructValue.StringValue(String(Character.toChars(it)))
              }
            } else {
              listOf()
            }

          val itemBits = bitLen / max(arrayLength, 1)
          for (i in 0 until arrayLength) {
            val itemVal = if (i < elements.size) elements[i] else defaultValue(primitiveType)
            val itemBitArray = encodePrimitiveToBits(value = itemVal, type = primitiveType)
            val targetStart = range.start + (i * itemBits)
            for (b in 0 until min(itemBits, itemBitArray.size)) {
              if (targetStart + b < bits.size) {
                bits[targetStart + b] = itemBitArray[b]
              }
            }
          }
        } else {
          val fieldValue = fieldVal ?: defaultValue(primitiveType)
          val fieldBitArray = encodePrimitiveToBits(value = fieldValue, type = primitiveType)
          for (b in 0 until min(bitLen, fieldBitArray.size)) {
            if (range.start + b < bits.size) {
              bits[range.start + b] = fieldBitArray[b]
            }
          }
        }
      } else if (childSchema != null) {
        // Child struct
        val arrayLength = valueSchema.arrayLength
        if (arrayLength != null) {
          val elements = if (fieldVal is StructValue.ArrayValue) fieldVal.elements else listOf()
          val childSchemaLen = childSchema.length
          for (i in 0 until arrayLength) {
            val childVal = if (i < elements.size) elements[i] else StructValue.ObjectValue(listOf())
            val childData = encodeInternal(name = valueSchema.type, value = childVal)
            if (childData != null) {
              val childBits = dataToBits(childData)
              val targetStart = range.start + (i * childSchemaLen)
              for (b in 0 until min(childSchemaLen, childBits.size)) {
                if (targetStart + b < bits.size) {
                  bits[targetStart + b] = childBits[b]
                }
              }
            }
          }
        } else {
          val childVal = fieldVal ?: StructValue.ObjectValue(listOf())
          val childData = encodeInternal(name = valueSchema.type, value = childVal)
          if (childData != null) {
            val childBits = dataToBits(childData)
            for (b in 0 until min(bitLen, childBits.size)) {
              if (range.start + b < bits.size) {
                bits[range.start + b] = childBits[b]
              }
            }
          }
        }
      }
    }

    return bitsToData(bits)
  }

  private fun encodePrimitiveToBits(value: StructValue, type: ValueType): BooleanArray {
    val rawData = ByteBuffer.allocate(type.maxBits / 8).order(ByteOrder.LITTLE_ENDIAN)

    // Integer payload shared by plain integers and enum values
    val intValue: Long? =
      when (value) {
        is StructValue.IntValue -> value.value
        is StructValue.EnumValue -> value.rawValue
        else -> null
      }

    when (type) {
      ValueType.BOOL -> if (value is StructValue.BoolValue) rawData.put(0, if (value.value) 1 else 0)
      ValueType.CHAR ->
        if (value is StructValue.StringValue) {
          value.value.toByteArray(Charsets.UTF_8).firstOrNull()?.let { rawData.put(0, it) }
        }
      ValueType.INT8 -> intValue?.let { rawData.put(0, it.toByte()) }
      ValueType.INT16 -> intValue?.let { rawData.putShort(0, it.toShort()) }
      ValueType.INT32 -> intValue?.let { rawData.putInt(0, it.toInt()) }
      ValueType.INT64 -> intValue?.let { rawData.putLong(0, it) }
      ValueType.UINT8 ->
        if (value is StructValue.UIntValue) {
          rawData.put(0, value.value.toByte())
        } else {
          intValue?.let { rawData.put(0, it.toByte()) }
        }
      ValueType.UINT16 ->
        if (value is StructValue.UIntValue) {
          rawData.putShort(0, value.value.toShort())
        } else {
          intValue?.let { rawData.putShort(0, it.toShort()) }
        }
      ValueType.UINT32 ->
        if (value is StructValue.UIntValue) {
          rawData.putInt(0, value.value.toInt())
        } else {
          intValue?.let { rawData.putInt(0, it.toInt()) }
        }
      ValueType.UINT64 ->
        if (value is StructValue.UIntValue) {
          rawData.putLong(0, value.value.toLong())
        } else {
          intValue?.let { rawData.putLong(0, it) }
        }
      ValueType.FLOAT,
      ValueType.FLOAT32 ->
        if (value is StructValue.FloatValue) {
          rawData.putInt(0, value.value.toRawBits())
        } else if (value is StructValue.DoubleValue) {
          rawData.putInt(0, value.value.toFloat().toRawBits())
        }
      ValueType.DOUBLE,
      ValueType.FLOAT64 ->
        if (value is StructValue.DoubleValue) {
          rawData.putLong(0, value.value.toRawBits())
        } else if (value is StructValue.FloatValue) {
          rawData.putLong(0, value.value.toDouble().toRawBits())
        }
    }

    return dataToBits(rawData.array())
  }

  private fun defaultValue(type: ValueType): StructValue {
    return when (type) {
      ValueType.BOOL -> StructValue.BoolValue(false)
      ValueType.CHAR -> StructValue.StringValue("\u0000")
      ValueType.INT8,
      ValueType.INT16,
      ValueType.INT32,
      ValueType.INT64 -> StructValue.IntValue(0)
      ValueType.UINT8,
      ValueType.UINT16,
      ValueType.UINT32,
      ValueType.UINT64 -> StructValue.UIntValue(0u)
      ValueType.FLOAT,
      ValueType.FLOAT32 -> StructValue.FloatValue(0f)
      ValueType.DOUBLE,
      ValueType.FLOAT64 -> StructValue.DoubleValue(0.0)
    }
  }

  private fun dataToBits(data: ByteArray): BooleanArray {
    val bits = BooleanArray(data.size * 8)
    for ((index, byte) in data.withIndex()) {
      for (shift in 0 until 8) {
        bits[index * 8 + shift] = ((byte.toInt() shr shift) and 1) != 0
      }
    }
    return bits
  }

  private fun bitsToData(bits: BooleanArray): ByteArray {
    val byteCount = (bits.size + 7) / 8
    val result = ByteArray(byteCount)
    for (i in bits.indices) {
      if (bits[i]) {
        val byteIdx = i / 8
        val bitIdx = i % 8
        result[byteIdx] = (result[byteIdx].toInt() or (1 shl bitIdx)).toByte()
      }
    }
    return result
  }

  private fun decodeInternal(name: String, data: ByteArray): StructValue? {
    val schema = schemas[name] ?: return null
    val resultObj = mutableListOf<Pair<String, StructValue>>()

    for (valueSchema in schema.valueSchemas) {
      val slicedData = sliceBits(data = data, range = valueSchema.bitRange)

      val primitiveType = ValueType.from(valueSchema.type)
      if (primitiveType != null) {
        val arrayLength = valueSchema.arrayLength
        if (arrayLength != null) {
          val arrayValues = mutableListOf<StructValue>()
          val totalBits = valueSchema.bitRange.end - valueSchema.bitRange.start
          val itemBits = totalBits / max(arrayLength, 1)

          for (i in 0 until arrayLength) {
            val itemRange = BitRange(i * itemBits, (i + 1) * itemBits)
            val itemData = sliceBits(data = slicedData, range = itemRange)
            arrayValues.add(
              decodePrimitive(data = itemData, type = primitiveType, enumData = valueSchema.enumData)
            )
          }

          if (primitiveType == ValueType.CHAR) {
            val fullString =
              arrayValues.joinToString("") { (it as? StructValue.StringValue)?.value ?: "" }
            val cString = fullString.takeWhile { it != '\u0000' }
            resultObj.add(valueSchema.name to StructValue.StringValue(cString))
          } else {
            resultObj.add(valueSchema.name to StructValue.ArrayValue(arrayValues))
          }
        } else {
          resultObj.add(
            valueSchema.name to
              decodePrimitive(
                data = slicedData,
                type = primitiveType,
                enumData = valueSchema.enumData,
              )
          )
        }
      } else if (schemas[valueSchema.type] != null) {
        // Child struct
        val arrayLength = valueSchema.arrayLength
        if (arrayLength != null) {
          val decodedArr =
            decodeArrayInternal(
              name = valueSchema.type,
              data = slicedData,
              arrayLength = arrayLength,
            )
          if (decodedArr != null) {
            resultObj.add(valueSchema.name to decodedArr)
          }
        } else {
          val child = decodeInternal(name = valueSchema.type, data = slicedData)
          if (child != null) {
            resultObj.add(valueSchema.name to child)
          }
        }
      }
    }

    return StructValue.ObjectValue(resultObj)
  }

  private fun decodeArrayInternal(name: String, data: ByteArray, arrayLength: Int? = null): StructValue? {
    val schema = schemas[name] ?: return null
    val itemByteCount = max(schema.length / 8, 1)
    val totalCount = arrayLength ?: (data.size / itemByteCount)

    val elements = mutableListOf<StructValue>()
    for (i in 0 until totalCount) {
      val start = i * itemByteCount
      val end = min(start + itemByteCount, data.size)
      if (start >= data.size) break
      val slice = data.copyOfRange(start, end)
      val item = decodeInternal(name = name, data = slice)
      if (item != null) {
        elements.add(item)
      }
    }

    return StructValue.ArrayValue(elements)
  }

  // MARK: - Primitive Value Decoding

  private fun decodePrimitive(data: ByteArray, type: ValueType, enumData: Map<Long, String>?): StructValue {
    val padded = ByteArray(type.maxBits / 8)
    val copyLen = min(data.size, padded.size)
    data.copyInto(padded, 0, 0, copyLen)
    val buffer = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)

    val output: StructValue =
      when (type) {
        ValueType.BOOL -> StructValue.BoolValue(padded[0].toInt() != 0)
        ValueType.CHAR -> StructValue.StringValue((padded[0].toInt() and 0xFF).toChar().toString())
        ValueType.INT8 -> StructValue.IntValue(padded[0].toLong())
        ValueType.INT16 -> StructValue.IntValue(buffer.getShort(0).toLong())
        ValueType.INT32 -> StructValue.IntValue(buffer.getInt(0).toLong())
        ValueType.INT64 -> StructValue.IntValue(buffer.getLong(0))
        ValueType.UINT8 -> StructValue.UIntValue(padded[0].toUByte().toULong())
        ValueType.UINT16 -> StructValue.UIntValue(buffer.getShort(0).toUShort().toULong())
        ValueType.UINT32 -> StructValue.UIntValue(buffer.getInt(0).toUInt().toULong())
        ValueType.UINT64 -> StructValue.UIntValue(buffer.getLong(0).toULong())
        ValueType.FLOAT,
        ValueType.FLOAT32 -> StructValue.FloatValue(Float.fromBits(buffer.getInt(0)))
        ValueType.DOUBLE,
        ValueType.FLOAT64 -> StructValue.DoubleValue(Double.fromBits(buffer.getLong(0)))
      }

    if (enumData != null) {
      if (output is StructValue.IntValue) {
        val intVal = output.value
        val enumName = enumData[intVal] ?: "$intVal"
        return StructValue.EnumValue(name = enumName, rawValue = intVal, options = enumData)
      }
      if (output is StructValue.UIntValue) {
        val raw = output.value.toLong()
        val enumName = enumData[raw] ?: "$raw"
        return StructValue.EnumValue(name = enumName, rawValue = raw, options = enumData)
      }
    }

    return output
  }

  // MARK: - Bit Slicing

  private fun sliceBits(data: ByteArray, range: BitRange): ByteArray {
    val bitLen = range.end - range.start
    if (bitLen <= 0) return ByteArray(0)

    if (range.start % 8 == 0 && range.end % 8 == 0) {
      val startByte = range.start / 8
      val endByte = min(range.end / 8, data.size)
      if (startByte >= data.size) return ByteArray(0)
      return data.copyOfRange(startByte, endByte)
    }

    // Bit-level slice
    val totalBits = data.size * 8
    if (range.start >= totalBits) return ByteArray(0)
    val slicedBitCount = min(range.end, totalBits) - range.start

    val byteCount = (slicedBitCount + 7) / 8
    val result = ByteArray(byteCount)
    for (i in 0 until slicedBitCount) {
      val sourceBit = range.start + i
      if (((data[sourceBit / 8].toInt() shr (sourceBit % 8)) and 1) != 0) {
        val byteIdx = i / 8
        val bitIdx = i % 8
        result[byteIdx] = (result[byteIdx].toInt() or (1 shl bitIdx)).toByte()
      }
    }
    return result
  }

  companion object {
    val shared = StructDecoder()
  }
}

// MARK: - Private Schema Types

private class Schema(val length: Int, val valueSchemas: List<ValueSchema>)

private class BitRange(val start: Int, val end: Int)

private class ValueSchema(
  val name: String,
  val type: String,
  val enumData: Map<Long, String>?,
  val bitfieldWidth: Int?,
  val arrayLength: Int?,
  var bitRange: BitRange,
)

private enum class ValueType(val rawValue: String) {
  BOOL("bool"),
  CHAR("char"),
  INT8("int8"),
  INT16("int16"),
  INT32("int32"),
  INT64("int64"),
  UINT8("uint8"),
  UINT16("uint16"),
  UINT32("uint32"),
  UINT64("uint64"),
  FLOAT("float"),
  FLOAT32("float32"),
  DOUBLE("double"),
  FLOAT64("float64");

  val maxBits: Int
    get() =
      when (this) {
        BOOL,
        CHAR,
        INT8,
        UINT8 -> 8
        INT16,
        UINT16 -> 16
        INT32,
        UINT32,
        FLOAT,
        FLOAT32 -> 32
        INT64,
        UINT64,
        DOUBLE,
        FLOAT64 -> 64
      }

  companion object {
    private val byRawValue = entries.associateBy { it.rawValue }

    fun from(rawValue: String): ValueType? = byRawValue[rawValue]
  }
}
