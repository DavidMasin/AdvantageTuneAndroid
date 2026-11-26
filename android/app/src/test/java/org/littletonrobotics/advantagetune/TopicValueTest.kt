// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import java.util.Locale
import okio.ByteString.Companion.decodeHex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.NumberFormatting
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.models.TopicNode
import org.littletonrobotics.advantagetune.models.TopicTreeBuilder
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.models.cleanFormatted
import org.littletonrobotics.advantagetune.models.localizedStandardCompare
import org.littletonrobotics.advantagetune.network.MessagePackStream

class TopicValueTest {
  private lateinit var originalLocale: Locale

  @Before
  fun setUp() {
    originalLocale = Locale.getDefault()
    Locale.setDefault(Locale.US)
  }

  @After
  fun tearDown() {
    Locale.setDefault(originalLocale)
  }

  @Test
  fun formatsDoublesCleanly() {
    assertEquals("0", 0.0.cleanFormatted)
    assertEquals("0", (-0.0).cleanFormatted)
    assertEquals("0", 1e-13.cleanFormatted)
    assertEquals("0", (-0.0000001).cleanFormatted)
    assertEquals("3.14159", 3.14159.cleanFormatted)
    assertEquals("0.3", (0.1 + 0.2).cleanFormatted)
    assertEquals("3.141593", Math.PI.cleanFormatted)
    assertEquals("-1.75", (-1.75).cleanFormatted)
    assertEquals("1234567", 1234567.0.cleanFormatted)
    assertEquals("NaN", Double.NaN.cleanFormatted)
    assertEquals("-Infinity", Double.NEGATIVE_INFINITY.cleanFormatted)
    assertEquals("1.41421", 1.41421f.cleanFormatted)
    assertEquals("0.1", 0.1f.cleanFormatted)
  }

  @Test
  fun formatsWithLocaleDecimalSeparator() {
    Locale.setDefault(Locale.FRANCE)
    assertEquals("3,14159", 3.14159.cleanFormatted)
    assertEquals(3.5, NumberFormatting.parseDouble("3,5")!!, 0.0)
    assertEquals(-3.5, NumberFormatting.parseDouble("-3.5")!!, 0.0)
  }

  @Test
  fun parsesNumbers() {
    assertEquals(0.05, NumberFormatting.parseDouble(" 0.05 ")!!, 0.0)
    assertEquals(-12.0, NumberFormatting.parseDouble("-12")!!, 0.0)
    assertEquals(null, NumberFormatting.parseDouble("abc"))
    assertEquals(null, NumberFormatting.parseDouble("1.2.3"))
    assertEquals(42L, NumberFormatting.parseLong("42"))
    assertEquals(-7L, NumberFormatting.parseLong("-7"))
    assertEquals(null, NumberFormatting.parseLong("1.5"))
  }

  @Test
  fun formatsValues() {
    assertEquals("true", TopicValue.BooleanValue(true).formattedString)
    assertEquals("6328", TopicValue.IntValue(6328).formattedString)
    assertEquals("[1, 1, 2, 3]", TopicValue.IntArrayValue(listOf(1, 1, 2, 3)).formattedString)
    assertEquals(
      "[\"Alpha\", \"Beta\"]",
      TopicValue.StringArrayValue(listOf("Alpha", "Beta")).formattedString,
    )
    assertEquals("8 bytes", TopicValue.RawValue("0102030405aabbcc".decodeHex()).formattedString)
    assertEquals(
      "01 02 03 04 05 AA BB CC",
      TopicValue.RawValue("0102030405aabbcc".decodeHex()).fullString,
    )
    assertEquals("a".repeat(57) + "…", TopicValue.StringValue("a".repeat(61)).formattedString)

    val long = TopicValue.DoubleArrayValue(List(300) { 0.5 }).formattedString
    assertEquals(198, long.length)
    assertEquals('…', long.last())

    val struct =
      StructValue.ObjectValue(
        listOf(
          "x" to StructValue.DoubleValue(2.5),
          "name" to StructValue.StringValue("A"),
          "mode" to StructValue.EnumValue("Teleop", 2),
        )
      )
    assertEquals("{x: 2.5, name: \"A\", mode: Teleop}", struct.formattedSummary)
    assertEquals("{\"x\": 2.5, \"name\": \"A\", \"mode\": Teleop (2)}", struct.fullString)
  }

  @Test
  fun convertsFromMessagePack() {
    assertEquals(
      TopicValue.DoubleValue(3.0),
      TopicValue.from(1, MessagePackStream.Value.IntValue(3)),
    )
    assertEquals(
      TopicValue.FloatArrayValue(listOf(1.5f, 2f)),
      TopicValue.from(
        19,
        MessagePackStream.Value.ArrayValue(
          listOf(MessagePackStream.Value.FloatValue(1.5f), MessagePackStream.Value.IntValue(2))
        ),
      ),
    )
    assertEquals(
      MessagePackStream.Value.BinaryValue("01".decodeHex()),
      TopicValue.RawValue("01".decodeHex()).toMessagePackStreamValue(),
    )
  }

  @Test
  fun sortsLikeFileNames() {
    val names = listOf("Item 10", "item 2", "Item 1", "alpha", "Beta", "_hidden", "Item 02")
    assertEquals(
      listOf("_hidden", "alpha", "Beta", "Item 1", "item 2", "Item 02", "Item 10"),
      names.sortedWith { a, b -> localizedStandardCompare(a, b) },
    )
  }

  @Test
  fun buildsTree() {
    val topics =
      listOf(
        NT4Topic(1, "/TeamNumber", "int"),
        NT4Topic(2, "/Drive/Module10/Angle", "double"),
        NT4Topic(3, "/Drive/Module2/Angle", "double"),
        NT4Topic(4, "/Drive/Enabled", "boolean"),
        NT4Topic(5, "/.schema/struct:Pose2d", "raw"),
        NT4Topic(6, "/Drive/.hidden/Value", "double"),
      )
    val tree = TopicTreeBuilder.buildTree(topics, mapOf("/TeamNumber" to TopicValue.IntValue(6328)))

    assertEquals(listOf("Drive", "TeamNumber"), tree.map { it.name })
    val drive = tree[0] as TopicNode.Folder
    assertEquals("/Drive", drive.path)
    assertEquals(listOf("Module2", "Module10", "Enabled"), drive.children.map { it.name })
    assertEquals(TopicValue.IntValue(6328), tree[1].latestValue)
    assertEquals("/Drive/Module2/Angle", drive.children[0].children[0].topic?.name)
  }
}
