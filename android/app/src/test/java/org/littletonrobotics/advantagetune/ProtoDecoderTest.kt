// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.littletonrobotics.advantagetune.decoder.ProtoDecoder
import org.littletonrobotics.advantagetune.decoder.ProtoSchemaRegistry
import org.littletonrobotics.advantagetune.models.StructValue

class ProtoDecoderTest {
  private fun field(
    name: String,
    number: Int,
    type: Type,
    label: Label = Label.LABEL_OPTIONAL,
    typeName: String? = null,
  ): FieldDescriptorProto {
    val builder =
      FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type).setLabel(label)
    typeName?.let { builder.setTypeName(it) }
    return builder.build()
  }

  private val file: FileDescriptorProto =
    FileDescriptorProto.newBuilder()
      .setName("geometry2d.proto")
      .setPackage("wpi.proto")
      .addMessageType(
        DescriptorProto.newBuilder()
          .setName("ProtobufTranslation2d")
          .addField(field("x", 1, Type.TYPE_DOUBLE))
          .addField(field("y", 2, Type.TYPE_DOUBLE))
      )
      .addMessageType(
        DescriptorProto.newBuilder()
          .setName("ProtobufSample")
          .addField(
            field("translation", 1, Type.TYPE_MESSAGE, typeName = ".wpi.proto.ProtobufTranslation2d")
          )
          .addField(field("count", 2, Type.TYPE_SINT32))
          .addField(field("name", 3, Type.TYPE_STRING))
          .addField(field("enabled", 4, Type.TYPE_BOOL))
          .addField(field("mode", 5, Type.TYPE_ENUM, typeName = ".wpi.proto.Mode"))
          .addField(field("gains", 6, Type.TYPE_DOUBLE, label = Label.LABEL_REPEATED))
          .addField(field("id", 7, Type.TYPE_UINT32))
          .addField(field("ratio", 8, Type.TYPE_FLOAT))
      )
      .addEnumType(
        EnumDescriptorProto.newBuilder()
          .setName("Mode")
          .addValue(EnumValueDescriptorProto.newBuilder().setName("DISABLED").setNumber(0))
          .addValue(EnumValueDescriptorProto.newBuilder().setName("TELEOP").setNumber(2))
      )
      .build()

  private fun decoder(descriptor: ByteArray = file.toByteArray()): ProtoDecoder {
    val registry = ProtoSchemaRegistry()
    registry.addDescriptor("proto:geometry2d.proto", descriptor.toByteString())
    return ProtoDecoder(registry)
  }

  private val modeOptions = mapOf(0L to "DISABLED", 2L to "TELEOP")

  @Test
  fun decodesMessage() {
    val data = "09000000000000044011000000000000fcbf".decodeHex()
    val decoded = decoder().decode("proto:wpi.proto.ProtobufTranslation2d", data)
    assertEquals(
      StructValue.ObjectValue(
        listOf("x" to StructValue.DoubleValue(2.5), "y" to StructValue.DoubleValue(-1.75))
      ),
      decoded,
    )
  }

  @Test
  fun fillsDefaultsForMissingFields() {
    val decoded = decoder().decode("proto:ProtobufSample", okio.ByteString.EMPTY)
    assertEquals(
      StructValue.ObjectValue(
        listOf(
          "translation" to StructValue.ObjectValue(listOf()),
          "count" to StructValue.IntValue(0),
          "name" to StructValue.StringValue(""),
          "enabled" to StructValue.BoolValue(false),
          "mode" to StructValue.EnumValue("DISABLED", 0, modeOptions),
          "gains" to StructValue.ArrayValue(listOf()),
          "id" to StructValue.UIntValue(0u),
          "ratio" to StructValue.FloatValue(0f),
        )
      ),
      decoded,
    )
  }

  @Test
  fun roundTripsNestedMessage() {
    val decoder = decoder()
    val value =
      StructValue.ObjectValue(
        listOf(
          "translation" to
            StructValue.ObjectValue(
              listOf("x" to StructValue.DoubleValue(1.5), "y" to StructValue.DoubleValue(2.0))
            ),
          "count" to StructValue.IntValue(-7),
          "name" to StructValue.StringValue("Auto"),
          "enabled" to StructValue.BoolValue(true),
          "mode" to StructValue.EnumValue("TELEOP", 2, modeOptions),
          "gains" to
            StructValue.ArrayValue(listOf(StructValue.DoubleValue(0.1), StructValue.DoubleValue(0.05))),
          "id" to StructValue.UIntValue(300u),
          "ratio" to StructValue.FloatValue(0.5f),
        )
      )

    val encoded = decoder.encode("proto:ProtobufSample", value)!!
    assertEquals(
      "0a12" + "09000000000000f83f" + "110000000000000040" + "100d" + "1a044175746f" + "2001" +
        "2802" + "319a9999999999b93f" + "319a9999999999a93f" + "38ac02" + "450000003f",
      encoded.hex(),
    )
    assertEquals(value, decoder.decode("proto:ProtobufSample", encoded))
  }

  @Test
  fun decodesPackedDoublesAndSkipsUnknownFields() {
    // Unknown field 15 (varint), then packed doubles in field 6
    val data = ("7801" + "3210" + "000000000000f03f" + "0000000000000040").decodeHex()
    val decoded = decoder().decode("proto:ProtobufSample", data) as StructValue.ObjectValue
    assertEquals(
      StructValue.ArrayValue(listOf(StructValue.DoubleValue(1.0), StructValue.DoubleValue(2.0))),
      decoded.fields[5].second,
    )
  }

  @Test
  fun acceptsDescriptorSets() {
    val set = FileDescriptorSet.newBuilder().addFile(file).build()
    val decoder = decoder(set.toByteArray())
    val decoded = decoder.decode("proto:ProtobufTranslation2d", okio.ByteString.EMPTY)
    assertTrue(decoded is StructValue.ObjectValue)
  }

  @Test
  fun returnsNullWithoutDescriptor() {
    assertNull(ProtoDecoder(ProtoSchemaRegistry()).decode("proto:Missing", okio.ByteString.EMPTY))
  }
}
