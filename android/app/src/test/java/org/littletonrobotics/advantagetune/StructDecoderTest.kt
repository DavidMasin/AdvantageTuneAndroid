// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littletonrobotics.advantagetune.decoder.StructDecoder
import org.littletonrobotics.advantagetune.models.StructValue

class StructDecoderTest {
  private fun decoder(vararg schemas: Pair<String, String>): StructDecoder {
    val decoder = StructDecoder()
    for ((name, schema) in schemas) {
      decoder.addSchema(name, schema.encodeUtf8())
    }
    return decoder
  }

  private fun translation(x: Double, y: Double): StructValue =
    StructValue.ObjectValue(
      listOf("x" to StructValue.DoubleValue(x), "y" to StructValue.DoubleValue(y))
    )

  @Test
  fun decodesAndEncodesDoubles() {
    val decoder = decoder("Translation2d" to "double x; double y;")
    val value = translation(2.5, -1.75)

    val encoded = decoder.encode("struct:Translation2d", value)!!
    assertEquals("0000000000000440000000000000fcbf", encoded.hex())
    assertEquals(value, decoder.decode("struct:Translation2d", encoded))
  }

  @Test
  fun decodesStructArrays() {
    val decoder = decoder("Translation2d" to "double x; double y;")
    val value = StructValue.ArrayValue(listOf(translation(0.0, 0.0), translation(1.5, 2.0)))

    val encoded = decoder.encode("struct:Translation2d[]", value)!!
    assertEquals(32, encoded.size)
    assertEquals(value, decoder.decode("struct:Translation2d[]", encoded))
  }

  @Test
  fun compilesSchemasInDependencyOrder() {
    val decoder = StructDecoder()
    decoder.addSchema(
      "PathPlan",
      "char name[12]; Translation2d start; Translation2d waypoints[2];".encodeUtf8(),
    )
    assertFalse(decoder.hasSchema("struct:PathPlan"))
    assertNull(decoder.decode("struct:PathPlan", ByteArray(60).toByteString()))

    decoder.addSchema("Translation2d", "double x; double y;".encodeUtf8())
    assertTrue(decoder.hasSchema("struct:PathPlan"))

    val value =
      StructValue.ObjectValue(
        listOf(
          "name" to StructValue.StringValue("AutoPathA"),
          "start" to translation(0.0, 0.0),
          "waypoints" to
            StructValue.ArrayValue(listOf(translation(1.5, 2.0), translation(3.0, 1.0))),
        )
      )
    val encoded = decoder.encode("struct:PathPlan", value)!!
    assertEquals(12 + 16 + 32, encoded.size)
    assertEquals("4175746f5061746841000000", encoded.substring(0, 12).hex())
    assertEquals(value, decoder.decode("struct:PathPlan", encoded))
  }

  @Test
  fun packsBitfieldsAndEnums() {
    val decoder =
      decoder(
        "DriverStation" to
          "enum { Disabled = 0, Autonomous = 1, Teleop = 2, Test = 3 } uint8 mode; " +
            "bool enabled:1; bool estop:1; bool dsAttached:1; bool fmsAttached:1; int8 errorCount:4;"
      )
    val options = mapOf(0L to "Disabled", 1L to "Autonomous", 2L to "Teleop", 3L to "Test")
    val value =
      StructValue.ObjectValue(
        listOf(
          "mode" to StructValue.EnumValue("Teleop", 2, options),
          "enabled" to StructValue.BoolValue(true),
          "estop" to StructValue.BoolValue(false),
          "dsAttached" to StructValue.BoolValue(true),
          "fmsAttached" to StructValue.BoolValue(false),
          "errorCount" to StructValue.IntValue(5),
        )
      )

    val encoded = decoder.encode("struct:DriverStation", value)!!
    assertEquals("0255", encoded.hex())
    assertEquals(value, decoder.decode("struct:DriverStation", encoded))
  }

  @Test
  fun decodesMixedPrimitives() {
    val decoder =
      decoder(
        "MotorConfig" to
          "double targetVelocity; double currentVelocity; double pidGains[3]; bool inverted; int32 currentLimit;"
      )
    val data =
      ("0000000000000000" + "0000000000000000" + "000000000000f03f" + "0000000000000040" +
          "0000000000000840" + "01" + "d6ffffff")
        .decodeHex()

    val decoded = decoder.decode("struct:MotorConfig", data) as StructValue.ObjectValue
    assertEquals(
      StructValue.ArrayValue(
        listOf(
          StructValue.DoubleValue(1.0),
          StructValue.DoubleValue(2.0),
          StructValue.DoubleValue(3.0),
        )
      ),
      decoded.fields[2].second,
    )
    assertEquals(StructValue.BoolValue(true), decoded.fields[3].second)
    assertEquals(StructValue.IntValue(-42), decoded.fields[4].second)
    assertEquals(data, decoder.encode("struct:MotorConfig", decoded))
  }

  private fun ByteArray.toByteString() = okio.ByteString.of(*this)
}
