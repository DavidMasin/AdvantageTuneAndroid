// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.decoder

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.models.hexString
import org.littletonrobotics.advantagetune.network.decodeUtf8

/** Dynamic Protobuf wire-format decoder and encoder using protobuf descriptors. */
class ProtoDecoder(private val registry: ProtoSchemaRegistry = ProtoSchemaRegistry.shared) {

  // MARK: - Decoding

  /** Decodes a protobuf binary buffer given its type name into a `StructValue`. */
  fun decode(typeName: String, data: ByteString): StructValue? {
    val descriptor = registry.findMessageDescriptor(typeName) ?: return null
    return decodeMessage(data = data.toByteArray(), descriptor = descriptor)
  }

  private fun decodeMessage(data: ByteArray, descriptor: DescriptorProto): StructValue {
    val fieldByNumber = descriptor.fieldList.associateBy { it.number }
    val fieldValues = HashMap<Int, MutableList<StructValue>>()

    var offset = 0
    while (offset < data.size) {
      val (tag, nextOffset) = decodeVarint(data = data, offset = offset) ?: break
      offset = nextOffset

      val fieldNumber = (tag ushr 3).toInt()
      val wireType = (tag and 0x7).toInt()

      val fieldDesc = fieldByNumber[fieldNumber]
      if (fieldDesc == null) {
        offset = skipWireField(data = data, offset = offset, wireType = wireType)
        continue
      }

      when (wireType) {
        0 -> { // Varint
          val varint = decodeVarint(data = data, offset = offset)
          if (varint != null) {
            offset = varint.second
            val value = decodeVarintField(varint.first, fieldDesc = fieldDesc)
            fieldValues.getOrPut(fieldNumber) { mutableListOf() }.add(value)
          }
        }

        1 -> { // 64-bit fixed
          if (offset + 8 <= data.size) {
            val value = decodeFixed64Field(data, offset, fieldDesc = fieldDesc)
            offset += 8
            fieldValues.getOrPut(fieldNumber) { mutableListOf() }.add(value)
          }
        }

        2 -> { // Length-delimited
          val varint = decodeVarint(data = data, offset = offset)
          if (varint != null) {
            val length = varint.first
            offset = varint.second
            if (length >= 0 && offset + length <= data.size) {
              val slice = data.copyOfRange(offset, offset + length.toInt())
              offset += length.toInt()

              val values = decodeLengthDelimitedField(slice, fieldDesc = fieldDesc)
              fieldValues.getOrPut(fieldNumber) { mutableListOf() }.addAll(values)
            }
          }
        }

        5 -> { // 32-bit fixed
          if (offset + 4 <= data.size) {
            val value = decodeFixed32Field(data, offset, fieldDesc = fieldDesc)
            offset += 4
            fieldValues.getOrPut(fieldNumber) { mutableListOf() }.add(value)
          }
        }

        else -> {}
      }
    }

    // Assemble StructValue.ObjectValue in descriptor field order
    val resultObj = mutableListOf<Pair<String, StructValue>>()
    for (field in descriptor.fieldList) {
      val values = fieldValues[field.number] ?: listOf()
      if (field.label == Label.LABEL_REPEATED) {
        resultObj.add(field.name to StructValue.ArrayValue(values.toList()))
      } else if (values.isNotEmpty()) {
        resultObj.add(field.name to values.last())
      } else {
        resultObj.add(field.name to defaultStructValue(field))
      }
    }

    return StructValue.ObjectValue(resultObj)
  }

  // MARK: - Wire Type Helpers

  /** Returns the decoded value (as unsigned 64-bit) and the offset after it. */
  private fun decodeVarint(data: ByteArray, offset: Int): Pair<Long, Int>? {
    var result = 0L
    var shift = 0
    var cur = offset
    while (cur < data.size) {
      val byte = data[cur].toInt()
      cur += 1
      result = result or ((byte and 0x7F).toLong() shl shift)
      if ((byte and 0x80) == 0) {
        return result to cur
      }
      shift += 7
      if (shift > 64) return null
    }
    return null
  }

  private fun decodeVarintField(raw: Long, fieldDesc: FieldDescriptorProto): StructValue {
    return when (fieldDesc.type) {
      Type.TYPE_BOOL -> StructValue.BoolValue(raw != 0L)
      Type.TYPE_ENUM -> {
        val enumDesc = registry.findEnumDescriptor(fieldDesc.typeName)
        if (enumDesc != null) {
          val options = LinkedHashMap<Long, String>()
          for (value in enumDesc.valueList) {
            options[value.number.toLong()] = value.name
          }
          val name = options[raw] ?: "$raw"
          StructValue.EnumValue(name = name, rawValue = raw, options = options)
        } else {
          StructValue.IntValue(raw)
        }
      }
      Type.TYPE_SINT32 -> {
        val decoded = (raw ushr 1) xor -(raw and 1)
        StructValue.IntValue(decoded.toInt().toLong())
      }
      Type.TYPE_SINT64 -> {
        val decoded = (raw ushr 1) xor -(raw and 1)
        StructValue.IntValue(decoded)
      }
      Type.TYPE_UINT32,
      Type.TYPE_UINT64 -> StructValue.UIntValue(raw.toULong())
      Type.TYPE_INT32,
      Type.TYPE_INT64 -> StructValue.IntValue(raw)
      else -> StructValue.IntValue(raw)
    }
  }

  private fun decodeFixed64Field(
    data: ByteArray,
    offset: Int,
    fieldDesc: FieldDescriptorProto,
  ): StructValue {
    val u64 = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong()
    return when (fieldDesc.type) {
      Type.TYPE_DOUBLE -> StructValue.DoubleValue(Double.fromBits(u64))
      Type.TYPE_SFIXED64 -> StructValue.IntValue(u64)
      else -> StructValue.UIntValue(u64.toULong())
    }
  }

  private fun decodeFixed32Field(
    data: ByteArray,
    offset: Int,
    fieldDesc: FieldDescriptorProto,
  ): StructValue {
    val u32 = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
    return when (fieldDesc.type) {
      Type.TYPE_FLOAT -> StructValue.FloatValue(Float.fromBits(u32))
      Type.TYPE_SFIXED32 -> StructValue.IntValue(u32.toLong())
      else -> StructValue.UIntValue(u32.toUInt().toULong())
    }
  }

  private fun decodeLengthDelimitedField(
    slice: ByteArray,
    fieldDesc: FieldDescriptorProto,
  ): List<StructValue> {
    return when (fieldDesc.type) {
      Type.TYPE_STRING -> listOf(StructValue.StringValue(decodeUtf8(slice) ?: ""))

      Type.TYPE_BYTES -> {
        val hex = slice.toByteString().hexString(16)
        listOf(StructValue.StringValue("0x$hex"))
      }

      Type.TYPE_MESSAGE -> {
        val subDesc = registry.findMessageDescriptor(fieldDesc.typeName)
        if (subDesc != null) {
          listOf(decodeMessage(data = slice, descriptor = subDesc))
        } else {
          listOf(StructValue.ObjectValue(listOf()))
        }
      }

      Type.TYPE_DOUBLE -> { // Packed repeated doubles
        val doubles = mutableListOf<StructValue>()
        val buffer = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN)
        var cur = 0
        while (cur + 8 <= slice.size) {
          doubles.add(StructValue.DoubleValue(Double.fromBits(buffer.getLong(cur))))
          cur += 8
        }
        doubles
      }

      Type.TYPE_FLOAT -> { // Packed repeated floats
        val floats = mutableListOf<StructValue>()
        val buffer = ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN)
        var cur = 0
        while (cur + 4 <= slice.size) {
          floats.add(StructValue.FloatValue(Float.fromBits(buffer.getInt(cur))))
          cur += 4
        }
        floats
      }

      else -> listOf(StructValue.StringValue("${slice.size} bytes"))
    }
  }

  private fun skipWireField(data: ByteArray, offset: Int, wireType: Int): Int {
    var cur = offset
    when (wireType) {
      0 -> { // Varint
        while (cur < data.size) {
          val b = data[cur].toInt()
          cur += 1
          if ((b and 0x80) == 0) break
        }
        return cur
      }
      1 -> return min(cur + 8, data.size) // 64-bit
      2 -> { // Length-delimited
        val varint = decodeVarint(data = data, offset = cur) ?: return data.size
        val (len, next) = varint
        if (len < 0 || len > data.size) return data.size
        return min(next + len.toInt(), data.size)
      }
      5 -> return min(cur + 4, data.size) // 32-bit
      else -> return data.size
    }
  }

  private fun defaultStructValue(field: FieldDescriptorProto): StructValue {
    return when (field.type) {
      Type.TYPE_BOOL -> StructValue.BoolValue(false)
      Type.TYPE_DOUBLE -> StructValue.DoubleValue(0.0)
      Type.TYPE_FLOAT -> StructValue.FloatValue(0.0f)
      Type.TYPE_INT32,
      Type.TYPE_INT64,
      Type.TYPE_SINT32,
      Type.TYPE_SINT64,
      Type.TYPE_SFIXED32,
      Type.TYPE_SFIXED64 -> StructValue.IntValue(0)
      Type.TYPE_UINT32,
      Type.TYPE_UINT64,
      Type.TYPE_FIXED32,
      Type.TYPE_FIXED64 -> StructValue.UIntValue(0u)
      Type.TYPE_STRING -> StructValue.StringValue("")
      Type.TYPE_ENUM -> {
        val enumDesc = registry.findEnumDescriptor(field.typeName)
        val firstVal = enumDesc?.valueList?.firstOrNull()
        if (enumDesc != null && firstVal != null) {
          val options = LinkedHashMap<Long, String>()
          for (value in enumDesc.valueList) {
            options[value.number.toLong()] = value.name
          }
          StructValue.EnumValue(
            name = firstVal.name,
            rawValue = firstVal.number.toLong(),
            options = options,
          )
        } else {
          StructValue.IntValue(0)
        }
      }
      Type.TYPE_MESSAGE -> StructValue.ObjectValue(listOf())
      else -> StructValue.StringValue("")
    }
  }

  // MARK: - Encoding

  /** Encodes a `StructValue` into a binary Protobuf buffer given its type name. */
  fun encode(typeName: String, value: StructValue): ByteString? {
    val descriptor = registry.findMessageDescriptor(typeName) ?: return null
    return encodeMessage(value = value, descriptor = descriptor)
  }

  private fun encodeMessage(value: StructValue, descriptor: DescriptorProto): ByteString {
    if (value !is StructValue.ObjectValue) return ByteString.EMPTY
    val fieldMap = value.fields.toMap()

    val output = Buffer()

    for (field in descriptor.fieldList) {
      val fieldValue = fieldMap[field.name] ?: continue

      if (field.label == Label.LABEL_REPEATED) {
        if (fieldValue is StructValue.ArrayValue) {
          for (elem in fieldValue.elements) {
            encodeField(field = field, value = elem, data = output)
          }
        }
      } else {
        encodeField(field = field, value = fieldValue, data = output)
      }
    }

    return output.readByteString()
  }

  private fun encodeField(field: FieldDescriptorProto, value: StructValue, data: Buffer) {
    val tag = ((field.number shl 3) or wireType(field.type)).toLong()

    when (field.type) {
      Type.TYPE_BOOL ->
        if (value is StructValue.BoolValue) {
          writeVarint(tag, data)
          writeVarint(if (value.value) 1 else 0, data)
        }

      Type.TYPE_DOUBLE -> {
        val d: Double =
          when (value) {
            is StructValue.DoubleValue -> value.value
            is StructValue.FloatValue -> value.value.toDouble()
            else -> return
          }
        writeVarint(tag, data)
        data.writeLongLe(d.toRawBits())
      }

      Type.TYPE_FLOAT -> {
        val f: Float =
          when (value) {
            is StructValue.FloatValue -> value.value
            is StructValue.DoubleValue -> value.value.toFloat()
            else -> return
          }
        writeVarint(tag, data)
        data.writeIntLe(f.toRawBits())
      }

      Type.TYPE_INT32,
      Type.TYPE_INT64,
      Type.TYPE_ENUM -> {
        val i: Long =
          when (value) {
            is StructValue.IntValue -> value.value
            is StructValue.EnumValue -> value.rawValue
            is StructValue.UIntValue -> value.value.toLong()
            else -> return
          }
        writeVarint(tag, data)
        writeVarint(i, data)
      }

      Type.TYPE_UINT32,
      Type.TYPE_UINT64 -> {
        val u: Long =
          when (value) {
            is StructValue.UIntValue -> value.value.toLong()
            is StructValue.IntValue -> max(0L, value.value)
            else -> return
          }
        writeVarint(tag, data)
        writeVarint(u, data)
      }

      Type.TYPE_SINT32,
      Type.TYPE_SINT64 -> {
        val i: Long = value.asInt64 ?: 0
        val zigzag = (i shl 1) xor (i shr 63)
        writeVarint(tag, data)
        writeVarint(zigzag, data)
      }

      Type.TYPE_STRING ->
        if (value is StructValue.StringValue) {
          val utf8 = value.value.encodeUtf8()
          writeVarint(tag, data)
          writeVarint(utf8.size.toLong(), data)
          data.write(utf8)
        }

      Type.TYPE_MESSAGE -> {
        val subDesc = registry.findMessageDescriptor(field.typeName)
        if (subDesc != null) {
          val subData = encodeMessage(value = value, descriptor = subDesc)
          writeVarint(tag, data)
          writeVarint(subData.size.toLong(), data)
          data.write(subData)
        }
      }

      else -> {}
    }
  }

  private fun wireType(type: Type): Int {
    return when (type) {
      Type.TYPE_DOUBLE,
      Type.TYPE_FIXED64,
      Type.TYPE_SFIXED64 -> 1
      Type.TYPE_FLOAT,
      Type.TYPE_FIXED32,
      Type.TYPE_SFIXED32 -> 5
      Type.TYPE_STRING,
      Type.TYPE_BYTES,
      Type.TYPE_MESSAGE -> 2
      else -> 0 // varint
    }
  }

  /** Writes the value (as unsigned 64-bit) in varint encoding. */
  private fun writeVarint(value: Long, data: Buffer) {
    var v = value
    while (v.toULong() >= 0x80u) {
      data.writeByte(((v and 0x7F) or 0x80).toInt())
      v = v ushr 7
    }
    data.writeByte((v and 0x7F).toInt())
  }

  private val StructValue.asInt64: Long?
    get() =
      when (this) {
        is StructValue.IntValue -> value
        is StructValue.UIntValue -> value.toLong()
        is StructValue.EnumValue -> rawValue
        else -> null
      }

  companion object {
    val shared = ProtoDecoder()
  }
}
