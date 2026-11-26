// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.network

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/** Streaming MessagePack decoder supporting concatenated multi-message frames. */
object MessagePackStream {

  sealed class Value {
    data object NullValue : Value()

    data class BoolValue(val value: Boolean) : Value()

    data class IntValue(val value: Long) : Value()

    data class UIntValue(val value: ULong) : Value()

    data class FloatValue(val value: Float) : Value()

    data class DoubleValue(val value: Double) : Value()

    data class StringValue(val value: String) : Value()

    data class BinaryValue(val value: ByteString) : Value()

    data class ArrayValue(val elements: List<Value>) : Value()

    data class MapValue(val entries: List<Pair<Value, Value>>) : Value()

    data class ExtValue(val type: Byte, val data: ByteString) : Value()

    val asInt64: Long?
      get() =
        when (this) {
          is IntValue -> value
          is UIntValue -> value.toLong()
          is DoubleValue -> value.toLong()
          is FloatValue -> value.toLong()
          is BoolValue -> if (value) 1 else 0
          else -> null
        }

    val asDouble: Double?
      get() =
        when (this) {
          is DoubleValue -> value
          is FloatValue -> value.toDouble()
          is IntValue -> value.toDouble()
          is UIntValue -> value.toDouble()
          is BoolValue -> if (value) 1.0 else 0.0
          else -> null
        }

    val asBool: Boolean?
      get() =
        when (this) {
          is BoolValue -> value
          is IntValue -> value != 0L
          is UIntValue -> value != 0UL
          else -> null
        }

    val asString: String?
      get() =
        when (this) {
          is StringValue -> value
          else -> null
        }

    val asBinary: ByteString?
      get() =
        when (this) {
          is BinaryValue -> value
          is StringValue -> value.encodeUtf8()
          else -> null
        }
  }

  // MARK: - Streaming Multi-Decode

  /** Unpacks all concatenated MessagePack objects contained in a data buffer. */
  fun unpackAll(data: ByteString): List<Value> = unpackAll(data.toByteArray())

  /** Unpacks all concatenated MessagePack objects contained in a data buffer. */
  fun unpackAll(data: ByteArray): List<Value> {
    val results = mutableListOf<Value>()
    var offset = 0
    val count = data.size

    while (offset < count) {
      val decoded = decodeValue(data, count, offset)
      if (decoded != null && decoded.bytesRead > 0) {
        results.add(decoded.value)
        offset += decoded.bytesRead
      } else {
        break
      }
    }

    return results
  }

  private class Decoded(val value: Value, val bytesRead: Int)

  private fun decodeValue(ptr: ByteArray, count: Int, offset: Int): Decoded? {
    if (offset >= count) return null
    val start = offset
    val b0 = ptr[start].toInt() and 0xFF

    // Positive fixint: 0x00 - 0x7f
    if (b0 <= 0x7f) {
      return Decoded(Value.IntValue(b0.toLong()), 1)
    }
    // Negative fixint: 0xe0 - 0xff
    if (b0 >= 0xe0) {
      return Decoded(Value.IntValue(b0.toByte().toLong()), 1)
    }
    // Fixmap: 0x80 - 0x8f
    if ((b0 and 0xf0) == 0x80) {
      val mapCount = b0 and 0x0f
      return decodeMap(ptr, count, start, headerSize = 1, numEntries = mapCount.toLong())
    }
    // Fixarray: 0x90 - 0x9f
    if ((b0 and 0xf0) == 0x90) {
      val arrCount = b0 and 0x0f
      return decodeArray(ptr, count, start, headerSize = 1, numElements = arrCount.toLong())
    }
    // Fixstr: 0xa0 - 0xbf
    if ((b0 and 0xe0) == 0xa0) {
      val strLen = b0 and 0x1f
      if (start + 1 + strLen > count) return null
      return Decoded(Value.StringValue(decodeString(ptr, start + 1, strLen)), 1 + strLen)
    }

    when (b0) {
      0xc0 -> return Decoded(Value.NullValue, 1) // nil
      0xc2 -> return Decoded(Value.BoolValue(false), 1) // false
      0xc3 -> return Decoded(Value.BoolValue(true), 1) // true

      0xc4 -> { // bin 8
        if (start + 2 > count) return null
        val len = readUInt8(ptr, start + 1).toLong()
        if (start + 2 + len > count) return null
        return Decoded(Value.BinaryValue(ptr.toByteString(start + 2, len.toInt())), 2 + len.toInt())
      }

      0xc5 -> { // bin 16
        if (start + 3 > count) return null
        val len = readUInt16(ptr, start + 1).toLong()
        if (start + 3 + len > count) return null
        return Decoded(Value.BinaryValue(ptr.toByteString(start + 3, len.toInt())), 3 + len.toInt())
      }

      0xc6 -> { // bin 32
        if (start + 5 > count) return null
        val len = readUInt32(ptr, start + 1)
        if (start + 5 + len > count) return null
        return Decoded(Value.BinaryValue(ptr.toByteString(start + 5, len.toInt())), 5 + len.toInt())
      }

      0xc7 -> { // ext 8
        if (start + 3 > count) return null
        val len = readUInt8(ptr, start + 1).toLong()
        val extType = ptr[start + 2]
        if (start + 3 + len > count) return null
        return Decoded(
          Value.ExtValue(extType, ptr.toByteString(start + 3, len.toInt())),
          3 + len.toInt(),
        )
      }

      0xc8 -> { // ext 16
        if (start + 4 > count) return null
        val len = readUInt16(ptr, start + 1).toLong()
        val extType = ptr[start + 3]
        if (start + 4 + len > count) return null
        return Decoded(
          Value.ExtValue(extType, ptr.toByteString(start + 4, len.toInt())),
          4 + len.toInt(),
        )
      }

      0xc9 -> { // ext 32
        if (start + 6 > count) return null
        val len = readUInt32(ptr, start + 1)
        val extType = ptr[start + 5]
        if (start + 6 + len > count) return null
        return Decoded(
          Value.ExtValue(extType, ptr.toByteString(start + 6, len.toInt())),
          6 + len.toInt(),
        )
      }

      0xca -> { // float 32
        if (start + 5 > count) return null
        val u = readUInt32(ptr, start + 1)
        return Decoded(Value.FloatValue(Float.fromBits(u.toInt())), 5)
      }

      0xcb -> { // float 64
        if (start + 9 > count) return null
        val u = readUInt64(ptr, start + 1)
        return Decoded(Value.DoubleValue(Double.fromBits(u)), 9)
      }

      0xcc -> { // uint 8
        if (start + 2 > count) return null
        return Decoded(Value.UIntValue(readUInt8(ptr, start + 1).toULong()), 2)
      }

      0xcd -> { // uint 16
        if (start + 3 > count) return null
        return Decoded(Value.UIntValue(readUInt16(ptr, start + 1).toULong()), 3)
      }

      0xce -> { // uint 32
        if (start + 5 > count) return null
        return Decoded(Value.UIntValue(readUInt32(ptr, start + 1).toULong()), 5)
      }

      0xcf -> { // uint 64
        if (start + 9 > count) return null
        return Decoded(Value.UIntValue(readUInt64(ptr, start + 1).toULong()), 9)
      }

      0xd0 -> { // int 8
        if (start + 2 > count) return null
        return Decoded(Value.IntValue(ptr[start + 1].toLong()), 2)
      }

      0xd1 -> { // int 16
        if (start + 3 > count) return null
        return Decoded(Value.IntValue(readUInt16(ptr, start + 1).toShort().toLong()), 3)
      }

      0xd2 -> { // int 32
        if (start + 5 > count) return null
        return Decoded(Value.IntValue(readUInt32(ptr, start + 1).toInt().toLong()), 5)
      }

      0xd3 -> { // int 64
        if (start + 9 > count) return null
        return Decoded(Value.IntValue(readUInt64(ptr, start + 1)), 9)
      }

      0xd4 -> { // fixext 1
        if (start + 3 > count) return null
        return Decoded(Value.ExtValue(ptr[start + 1], ptr.toByteString(start + 2, 1)), 3)
      }

      0xd5 -> { // fixext 2
        if (start + 4 > count) return null
        return Decoded(Value.ExtValue(ptr[start + 1], ptr.toByteString(start + 2, 2)), 4)
      }

      0xd6 -> { // fixext 4
        if (start + 6 > count) return null
        return Decoded(Value.ExtValue(ptr[start + 1], ptr.toByteString(start + 2, 4)), 6)
      }

      0xd7 -> { // fixext 8
        if (start + 10 > count) return null
        return Decoded(Value.ExtValue(ptr[start + 1], ptr.toByteString(start + 2, 8)), 10)
      }

      0xd8 -> { // fixext 16
        if (start + 18 > count) return null
        return Decoded(Value.ExtValue(ptr[start + 1], ptr.toByteString(start + 2, 16)), 18)
      }

      0xd9 -> { // str 8
        if (start + 2 > count) return null
        val len = readUInt8(ptr, start + 1).toLong()
        if (start + 2 + len > count) return null
        return Decoded(Value.StringValue(decodeString(ptr, start + 2, len.toInt())), 2 + len.toInt())
      }

      0xda -> { // str 16
        if (start + 3 > count) return null
        val len = readUInt16(ptr, start + 1).toLong()
        if (start + 3 + len > count) return null
        return Decoded(Value.StringValue(decodeString(ptr, start + 3, len.toInt())), 3 + len.toInt())
      }

      0xdb -> { // str 32
        if (start + 5 > count) return null
        val len = readUInt32(ptr, start + 1)
        if (start + 5 + len > count) return null
        return Decoded(Value.StringValue(decodeString(ptr, start + 5, len.toInt())), 5 + len.toInt())
      }

      0xdc -> { // array 16
        if (start + 3 > count) return null
        val len = readUInt16(ptr, start + 1).toLong()
        return decodeArray(ptr, count, start, headerSize = 3, numElements = len)
      }

      0xdd -> { // array 32
        if (start + 5 > count) return null
        val len = readUInt32(ptr, start + 1)
        return decodeArray(ptr, count, start, headerSize = 5, numElements = len)
      }

      0xde -> { // map 16
        if (start + 3 > count) return null
        val len = readUInt16(ptr, start + 1).toLong()
        return decodeMap(ptr, count, start, headerSize = 3, numEntries = len)
      }

      0xdf -> { // map 32
        if (start + 5 > count) return null
        val len = readUInt32(ptr, start + 1)
        return decodeMap(ptr, count, start, headerSize = 5, numEntries = len)
      }

      else -> return null
    }
  }

  private fun decodeArray(
    ptr: ByteArray,
    count: Int,
    start: Int,
    headerSize: Int,
    numElements: Long,
  ): Decoded? {
    // Every element takes at least one byte, so a larger count cannot be decoded
    if (numElements > count - start) return null

    val elements = ArrayList<Value>(numElements.toInt())
    var cur = start + headerSize

    for (i in 0 until numElements) {
      val decoded = decodeValue(ptr, count, cur)
      if (decoded == null || decoded.bytesRead <= 0) {
        return null
      }
      elements.add(decoded.value)
      cur += decoded.bytesRead
    }

    return Decoded(Value.ArrayValue(elements), cur - start)
  }

  private fun decodeMap(
    ptr: ByteArray,
    count: Int,
    start: Int,
    headerSize: Int,
    numEntries: Long,
  ): Decoded? {
    // Every entry takes at least two bytes, so a larger count cannot be decoded
    if (numEntries > count - start) return null

    val map = ArrayList<Pair<Value, Value>>(numEntries.toInt())
    var cur = start + headerSize

    for (i in 0 until numEntries) {
      val key = decodeValue(ptr, count, cur)
      if (key == null || key.bytesRead <= 0) {
        return null
      }
      cur += key.bytesRead
      val value = decodeValue(ptr, count, cur)
      if (value == null || value.bytesRead <= 0) {
        return null
      }
      cur += value.bytesRead
      map.add(key.value to value.value)
    }

    return Decoded(Value.MapValue(map), cur - start)
  }

  /** Decodes UTF-8 text, returning an empty string if the bytes are not valid UTF-8. */
  private fun decodeString(ptr: ByteArray, offset: Int, length: Int): String {
    return decodeUtf8(ptr, offset, length) ?: ""
  }

  // MARK: - Big Endian Helpers

  private fun readUInt8(ptr: ByteArray, offset: Int): Int {
    return ptr[offset].toInt() and 0xFF
  }

  private fun readUInt16(ptr: ByteArray, offset: Int): Int {
    return (readUInt8(ptr, offset) shl 8) or readUInt8(ptr, offset + 1)
  }

  private fun readUInt32(ptr: ByteArray, offset: Int): Long {
    return (readUInt8(ptr, offset).toLong() shl 24) or
      (readUInt8(ptr, offset + 1).toLong() shl 16) or
      (readUInt8(ptr, offset + 2).toLong() shl 8) or
      readUInt8(ptr, offset + 3).toLong()
  }

  private fun readUInt64(ptr: ByteArray, offset: Int): Long {
    return (readUInt32(ptr, offset) shl 32) or readUInt32(ptr, offset + 4)
  }

  // MARK: - Encoding

  fun pack(value: Value): ByteString {
    val data = Buffer()
    encode(value, data)
    return data.readByteString()
  }

  private fun encode(value: Value, data: Buffer) {
    when (value) {
      is Value.NullValue -> data.writeByte(0xc0)
      is Value.BoolValue -> data.writeByte(if (value.value) 0xc3 else 0xc2)
      is Value.IntValue -> {
        val i = value.value
        if (i in 0..127) {
          data.writeByte(i.toInt())
        } else if (i >= -32 && i < 0) {
          data.writeByte(i.toInt())
        } else if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
          data.writeByte(0xd0)
          data.writeByte(i.toInt())
        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
          data.writeByte(0xd1)
          data.writeShort(i.toInt())
        } else if (i >= Int.MIN_VALUE && i <= Int.MAX_VALUE) {
          data.writeByte(0xd2)
          data.writeInt(i.toInt())
        } else {
          data.writeByte(0xd3)
          data.writeLong(i)
        }
      }
      is Value.UIntValue -> {
        val u = value.value
        if (u <= 127UL) {
          data.writeByte(u.toInt())
        } else if (u <= UByte.MAX_VALUE.toULong()) {
          data.writeByte(0xcc)
          data.writeByte(u.toInt())
        } else if (u <= UShort.MAX_VALUE.toULong()) {
          data.writeByte(0xcd)
          data.writeShort(u.toInt())
        } else if (u <= UInt.MAX_VALUE.toULong()) {
          data.writeByte(0xce)
          data.writeInt(u.toInt())
        } else {
          data.writeByte(0xcf)
          data.writeLong(u.toLong())
        }
      }
      is Value.FloatValue -> {
        data.writeByte(0xca)
        data.writeInt(value.value.toRawBits())
      }
      is Value.DoubleValue -> {
        data.writeByte(0xcb)
        data.writeLong(value.value.toRawBits())
      }
      is Value.StringValue -> {
        val utf8 = value.value.encodeUtf8()
        val len = utf8.size
        if (len <= 31) {
          data.writeByte(0xa0 or len)
        } else if (len <= 0xFF) {
          data.writeByte(0xd9)
          data.writeByte(len)
        } else if (len <= 0xFFFF) {
          data.writeByte(0xda)
          data.writeShort(len)
        } else {
          data.writeByte(0xdb)
          data.writeInt(len)
        }
        data.write(utf8)
      }
      is Value.BinaryValue -> {
        val len = value.value.size
        if (len <= 0xFF) {
          data.writeByte(0xc4)
          data.writeByte(len)
        } else if (len <= 0xFFFF) {
          data.writeByte(0xc5)
          data.writeShort(len)
        } else {
          data.writeByte(0xc6)
          data.writeInt(len)
        }
        data.write(value.value)
      }
      is Value.ArrayValue -> {
        val count = value.elements.size
        if (count <= 15) {
          data.writeByte(0x90 or count)
        } else if (count <= 0xFFFF) {
          data.writeByte(0xdc)
          data.writeShort(count)
        } else {
          data.writeByte(0xdd)
          data.writeInt(count)
        }
        for (elem in value.elements) {
          encode(elem, data)
        }
      }
      is Value.MapValue -> {
        val count = value.entries.size
        if (count <= 15) {
          data.writeByte(0x80 or count)
        } else if (count <= 0xFFFF) {
          data.writeByte(0xde)
          data.writeShort(count)
        } else {
          data.writeByte(0xdf)
          data.writeInt(count)
        }
        for (item in value.entries) {
          encode(item.first, data)
          encode(item.second, data)
        }
      }
      is Value.ExtValue -> {
        data.writeByte(0xc7)
        data.writeByte(value.data.size)
        data.writeByte(value.type.toInt())
        data.write(value.data)
      }
    }
  }
}

/** Decodes UTF-8 text, returning null if the bytes are not valid UTF-8. */
fun decodeUtf8(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String? {
  return try {
    Charsets.UTF_8
      .newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(bytes, offset, length))
      .toString()
  } catch (e: CharacterCodingException) {
    null
  }
}

/** Decodes UTF-8 text, returning null if the bytes are not valid UTF-8. */
fun ByteString.decodeUtf8OrNull(): String? = decodeUtf8(toByteArray())
