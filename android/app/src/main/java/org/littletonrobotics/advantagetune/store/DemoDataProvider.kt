// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.store

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.littletonrobotics.advantagetune.decoder.StructDecoder
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.models.TopicValue

object DemoDataProvider {

  class DemoData(val topics: List<NT4Topic>, val values: Map<String, TopicValue>)

  fun loadDemoData(): DemoData {
    // Register struct schemas
    val translationSchema = "double x; double y;"
    val motorConfigSchema =
      "double targetVelocity; double currentVelocity; double pidGains[3]; bool inverted; int32 currentLimit;"
    val driverStationSchema =
      "enum { Disabled = 0, Autonomous = 1, Teleop = 2, Test = 3 } uint8 mode; bool enabled:1; bool estop:1; bool dsAttached:1; bool fmsAttached:1; int8 errorCount:4;"
    val pathPlanSchema = "char name[12]; Translation2d start; Translation2d waypoints[2];"

    StructDecoder.shared.addSchema(name = "Translation2d", schemaData = translationSchema.encodeUtf8())
    StructDecoder.shared.addSchema(name = "MotorConfig", schemaData = motorConfigSchema.encodeUtf8())
    StructDecoder.shared.addSchema(
      name = "DriverStation",
      schemaData = driverStationSchema.encodeUtf8(),
    )
    StructDecoder.shared.addSchema(name = "PathPlan", schemaData = pathPlanSchema.encodeUtf8())

    // Build struct values
    val translationValue =
      StructValue.ObjectValue(
        listOf("x" to StructValue.DoubleValue(2.5), "y" to StructValue.DoubleValue(-1.75))
      )
    val translationRaw =
      StructDecoder.shared.encode(typeName = "struct:Translation2d", value = translationValue)
        ?: ByteString.EMPTY

    val motorConfigValue =
      StructValue.ObjectValue(
        listOf(
          "targetVelocity" to StructValue.DoubleValue(3000.0),
          "currentVelocity" to StructValue.DoubleValue(2998.5),
          "pidGains" to
            StructValue.ArrayValue(
              listOf(
                StructValue.DoubleValue(0.1),
                StructValue.DoubleValue(0.0),
                StructValue.DoubleValue(0.05),
              )
            ),
          "inverted" to StructValue.BoolValue(false),
          "currentLimit" to StructValue.IntValue(40),
        )
      )
    val motorConfigRaw =
      StructDecoder.shared.encode(typeName = "struct:MotorConfig", value = motorConfigValue)
        ?: ByteString.EMPTY

    val driverStationValue =
      StructValue.ObjectValue(
        listOf(
          "mode" to
            StructValue.EnumValue(
              name = "Teleop",
              rawValue = 2,
              options = mapOf(0L to "Disabled", 1L to "Autonomous", 2L to "Teleop", 3L to "Test"),
            ),
          "enabled" to StructValue.BoolValue(true),
          "estop" to StructValue.BoolValue(false),
          "dsAttached" to StructValue.BoolValue(true),
          "fmsAttached" to StructValue.BoolValue(false),
          "errorCount" to StructValue.IntValue(0),
        )
      )
    val driverStationRaw =
      StructDecoder.shared.encode(typeName = "struct:DriverStation", value = driverStationValue)
        ?: ByteString.EMPTY

    val pathPlanValue =
      StructValue.ObjectValue(
        listOf(
          "name" to StructValue.StringValue("AutoPathA"),
          "start" to translation(0.0, 0.0),
          "waypoints" to StructValue.ArrayValue(listOf(translation(1.5, 2.0), translation(3.0, 1.0))),
        )
      )
    val pathPlanRaw =
      StructDecoder.shared.encode(typeName = "struct:PathPlan", value = pathPlanValue)
        ?: ByteString.EMPTY

    val waypointsValue =
      StructValue.ArrayValue(
        listOf(
          translation(0.0, 0.0),
          translation(1.5, 2.0),
          translation(3.0, 1.0),
          translation(4.5, -0.5),
        )
      )
    val waypointsRaw =
      StructDecoder.shared.encode(typeName = "struct:Translation2d[]", value = waypointsValue)
        ?: ByteString.EMPTY

    // Define topics and values
    val topics = mutableListOf<NT4Topic>()
    val values = LinkedHashMap<String, TopicValue>()

    // Root topic
    val teamNumberTopic = NT4Topic(id = 1, name = "/TeamNumber", type = "int")
    topics.add(teamNumberTopic)
    values[teamNumberTopic.name] = TopicValue.IntValue(6328)

    // Struct topics
    val tTranslation =
      NT4Topic(id = 10, name = "/Structs/Translation2d", type = "struct:Translation2d")
    topics.add(tTranslation)
    values[tTranslation.name] =
      TopicValue.Struct(typeName = "struct:Translation2d", value = translationValue, raw = translationRaw)

    val tMotorConfig = NT4Topic(id = 11, name = "/Structs/MotorConfig", type = "struct:MotorConfig")
    topics.add(tMotorConfig)
    values[tMotorConfig.name] =
      TopicValue.Struct(typeName = "struct:MotorConfig", value = motorConfigValue, raw = motorConfigRaw)

    val tDriverStation =
      NT4Topic(id = 12, name = "/Structs/DriverStation", type = "struct:DriverStation")
    topics.add(tDriverStation)
    values[tDriverStation.name] =
      TopicValue.Struct(
        typeName = "struct:DriverStation",
        value = driverStationValue,
        raw = driverStationRaw,
      )

    val tPathPlan = NT4Topic(id = 13, name = "/Structs/PathPlan", type = "struct:PathPlan")
    topics.add(tPathPlan)
    values[tPathPlan.name] =
      TopicValue.Struct(typeName = "struct:PathPlan", value = pathPlanValue, raw = pathPlanRaw)

    val tWaypoints = NT4Topic(id = 14, name = "/Structs/Waypoints", type = "struct:Translation2d[]")
    topics.add(tWaypoints)
    values[tWaypoints.name] =
      TopicValue.Struct(typeName = "struct:Translation2d[]", value = waypointsValue, raw = waypointsRaw)

    // Primitive topics
    val pBool = NT4Topic(id = 20, name = "/Primitives/Boolean", type = "boolean")
    val pDouble = NT4Topic(id = 21, name = "/Primitives/Double", type = "double")
    val pInt = NT4Topic(id = 22, name = "/Primitives/Integer", type = "int")
    val pFloat = NT4Topic(id = 23, name = "/Primitives/Float", type = "float")
    val pString = NT4Topic(id = 24, name = "/Primitives/String", type = "string")
    val pRaw = NT4Topic(id = 26, name = "/Primitives/Raw", type = "raw")

    topics.addAll(listOf(pBool, pDouble, pInt, pFloat, pString, pRaw))
    values[pBool.name] = TopicValue.BooleanValue(true)
    values[pDouble.name] = TopicValue.DoubleValue(3.14159)
    values[pInt.name] = TopicValue.IntValue(42)
    values[pFloat.name] = TopicValue.FloatValue(1.41421f)
    values[pString.name] = TopicValue.StringValue("Littleton")
    values[pRaw.name] =
      TopicValue.RawValue(
        byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
          .toByteString()
      )

    // Array topics
    val aBool = NT4Topic(id = 30, name = "/Arrays/BooleanArray", type = "boolean[]")
    val aDouble = NT4Topic(id = 31, name = "/Arrays/DoubleArray", type = "double[]")
    val aInt = NT4Topic(id = 32, name = "/Arrays/IntegerArray", type = "int[]")
    val aFloat = NT4Topic(id = 33, name = "/Arrays/FloatArray", type = "float[]")
    val aString = NT4Topic(id = 34, name = "/Arrays/StringArray", type = "string[]")

    topics.addAll(listOf(aBool, aDouble, aInt, aFloat, aString))
    values[aBool.name] = TopicValue.BooleanArrayValue(listOf(true, false, true, true, false))
    values[aDouble.name] =
      TopicValue.DoubleArrayValue(listOf(3.14159, 2.71828, 2.66514, 0.11, 0.00787))
    values[aInt.name] = TopicValue.IntArrayValue(listOf(1, 1, 2, 3, 5, 8, 13, 21))
    values[aFloat.name] = TopicValue.FloatArrayValue(listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f))
    values[aString.name] =
      TopicValue.StringArrayValue(listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon"))

    // Schema announcement topics
    val sTranslation = NT4Topic(id = 100, name = "/.schema/struct:Translation2d", type = "raw")
    val sMotorConfig = NT4Topic(id = 101, name = "/.schema/struct:MotorConfig", type = "raw")
    val sDriverStation = NT4Topic(id = 102, name = "/.schema/struct:DriverStation", type = "raw")
    val sPathPlan = NT4Topic(id = 103, name = "/.schema/struct:PathPlan", type = "raw")
    topics.addAll(listOf(sTranslation, sMotorConfig, sDriverStation, sPathPlan))
    values[sTranslation.name] = TopicValue.RawValue(translationSchema.encodeUtf8())
    values[sMotorConfig.name] = TopicValue.RawValue(motorConfigSchema.encodeUtf8())
    values[sDriverStation.name] = TopicValue.RawValue(driverStationSchema.encodeUtf8())
    values[sPathPlan.name] = TopicValue.RawValue(pathPlanSchema.encodeUtf8())

    return DemoData(topics, values)
  }

  private fun translation(x: Double, y: Double): StructValue {
    return StructValue.ObjectValue(
      listOf("x" to StructValue.DoubleValue(x), "y" to StructValue.DoubleValue(y))
    )
  }
}
