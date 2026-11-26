// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import okio.ByteString.Companion.decodeHex
import org.junit.Assert.assertEquals
import org.junit.Test
import org.littletonrobotics.advantagetune.network.MessagePackStream
import org.littletonrobotics.advantagetune.network.MessagePackStream.Value

class MessagePackStreamTest {
  @Test
  fun packsIntegersInSmallestFormat() {
    assertEquals("00", pack(Value.IntValue(0)))
    assertEquals("7f", pack(Value.IntValue(127)))
    assertEquals("ff", pack(Value.IntValue(-1)))
    assertEquals("e0", pack(Value.IntValue(-32)))
    assertEquals("d0df", pack(Value.IntValue(-33)))
    assertEquals("d10080", pack(Value.IntValue(128)))
    assertEquals("d200008000", pack(Value.IntValue(32768)))
    assertEquals("d30000000080000000", pack(Value.IntValue(2147483648)))
    assertEquals("cc80", pack(Value.UIntValue(128u)))
    assertEquals("cfffffffffffffffff", pack(Value.UIntValue(ULong.MAX_VALUE)))
  }

  @Test
  fun packsValueUpdateFrame() {
    val frame =
      Value.ArrayValue(
        listOf(
          Value.IntValue(3),
          Value.IntValue(1_000_000),
          Value.IntValue(1),
          Value.DoubleValue(1.5),
        )
      )
    assertEquals("9403d2000f424001cb3ff8000000000000", pack(frame))
  }

  @Test
  fun roundTripsAllValueKinds() {
    val value =
      Value.ArrayValue(
        listOf(
          Value.NullValue,
          Value.BoolValue(true),
          Value.IntValue(-123456789012),
          Value.UIntValue(200u),
          Value.FloatValue(1.25f),
          Value.DoubleValue(-2.5),
          Value.StringValue("héllo wörld, this string is longer than 31 bytes"),
          Value.BinaryValue("0102aabb".decodeHex()),
          Value.ArrayValue((0 until 20).map { Value.IntValue(it.toLong()) }),
          Value.MapValue(listOf(Value.StringValue("key") to Value.BoolValue(false))),
        )
      )
    val unpacked = MessagePackStream.unpackAll(MessagePackStream.pack(value))
    // Small unsigned values are written as positive fixints or uint 8
    assertEquals(listOf(value), unpacked)
  }

  @Test
  fun unpacksConcatenatedMessages() {
    val data = "9403d2000f424001cb3ff8000000000000" + "94ff0002c3" + "a3616263"
    val values = MessagePackStream.unpackAll(data.decodeHex())
    assertEquals(3, values.size)
    assertEquals(Value.StringValue("abc"), values[2])
    assertEquals(-1L, (values[1] as Value.ArrayValue).elements[0].asInt64)
  }

  @Test
  fun stopsAtTruncatedMessage() {
    val values = MessagePackStream.unpackAll("01cb3ff8".decodeHex())
    assertEquals(listOf<Value>(Value.IntValue(1)), values)
    assertEquals(listOf<Value>(), MessagePackStream.unpackAll("ddffffffff01".decodeHex()))
  }

  private fun pack(value: Value): String = MessagePackStream.pack(value).hex()
}
