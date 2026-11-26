// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.decoder

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.InvalidProtocolBufferException
import okio.ByteString

/** Registry for Protobuf `FileDescriptorProto` schemas announced over NetworkTables. */
class ProtoSchemaRegistry {
  private val fileDescriptors: MutableMap<String, FileDescriptorProto> = HashMap()
  private val rawDescriptors: MutableMap<String, ByteString> = HashMap()
  private val messageDescriptors: MutableMap<String, DescriptorProto> = HashMap()
  private val enumDescriptors: MutableMap<String, EnumDescriptorProto> = HashMap()

  /**
   * Registers a binary protobuf file descriptor announced under `/.schema/proto:<TypeName>` or
   * `/AdvantageKit/.schema/proto:<TypeName>`.
   */
  @Synchronized
  fun addDescriptor(name: String, data: ByteString) {
    val cleanName = cleanTypeName(name)
    rawDescriptors[cleanName] = data

    val bytes = data.toByteArray()
    val fileDescriptor = parseFileDescriptor(bytes)
    if (fileDescriptor != null) {
      fileDescriptors[cleanName] = fileDescriptor
      indexFile(fileDescriptor, registeredName = cleanName)
    } else {
      val set =
        try {
          FileDescriptorSet.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
          return
        }
      for (file in set.fileList) {
        fileDescriptors[file.name] = file
        indexFile(file, registeredName = cleanName)
      }
    }
  }

  private fun parseFileDescriptor(bytes: ByteArray): FileDescriptorProto? {
    val file =
      try {
        FileDescriptorProto.parseFrom(bytes)
      } catch (e: InvalidProtocolBufferException) {
        return null
      }
    // A descriptor set parses as a file whose name holds the (binary) nested file
    if (!file.nameBytes.isValidUtf8 || !file.packageBytes.isValidUtf8) return null
    return file
  }

  private fun indexFile(file: FileDescriptorProto, registeredName: String) {
    val pkg = file.`package`

    for (msg in file.messageTypeList) {
      indexMessage(msg, packageName = pkg, registeredName = registeredName)
    }

    for (en in file.enumTypeList) {
      val fullName = if (pkg.isEmpty()) en.name else "$pkg.${en.name}"
      enumDescriptors[en.name] = en
      enumDescriptors[fullName] = en
      enumDescriptors[".$fullName"] = en
    }
  }

  private fun indexMessage(msg: DescriptorProto, packageName: String, registeredName: String) {
    val fullName = if (packageName.isEmpty()) msg.name else "$packageName.${msg.name}"

    messageDescriptors[msg.name] = msg
    messageDescriptors[fullName] = msg
    messageDescriptors[".$fullName"] = msg
    messageDescriptors[registeredName] = msg

    // Also index with simple message name if registeredName has package
    if (registeredName.contains(".")) {
      val lastPart = registeredName.split(".").last()
      messageDescriptors[lastPart] = msg
    }

    for (nested in msg.nestedTypeList) {
      val nestedPkg = fullName
      indexMessage(
        nested,
        packageName = nestedPkg,
        registeredName = "$registeredName.${nested.name}",
      )
    }

    for (en in msg.enumTypeList) {
      val enumFullName = "$fullName.${en.name}"
      enumDescriptors[en.name] = en
      enumDescriptors[enumFullName] = en
      enumDescriptors[".$enumFullName"] = en
    }
  }

  /** Checks if a protobuf schema is registered. */
  @Synchronized
  fun hasDescriptor(typeName: String): Boolean {
    val clean = cleanTypeName(typeName)
    return messageDescriptors[clean] != null || fileDescriptors[clean] != null ||
      rawDescriptors[clean] != null
  }

  /** Finds a message descriptor by name or type path. */
  @Synchronized
  fun findMessageDescriptor(typeName: String): DescriptorProto? {
    val clean = cleanTypeName(typeName)
    messageDescriptors[clean]?.let { return it }
    messageDescriptors[".$clean"]?.let { return it }
    messageDescriptors[clean.split(".").last()]?.let { return it }
    return null
  }

  /** Finds an enum descriptor by name or type path. */
  @Synchronized
  fun findEnumDescriptor(typeName: String): EnumDescriptorProto? {
    val clean = cleanTypeName(typeName)
    enumDescriptors[clean]?.let { return it }
    enumDescriptors[".$clean"]?.let { return it }
    enumDescriptors[clean.split(".").last()]?.let { return it }
    return null
  }

  /** Returns the parsed FileDescriptorProto if available. */
  @Synchronized
  fun descriptor(typeName: String): FileDescriptorProto? {
    val clean = cleanTypeName(typeName)
    return fileDescriptors[clean]
  }

  fun cleanTypeName(typeName: String): String {
    var clean = typeName
    if (clean.startsWith("proto:")) {
      clean = clean.substring("proto:".length)
    }
    if (clean.endsWith("[]")) {
      clean = clean.substring(0, clean.length - 2)
    }
    return clean
  }

  companion object {
    val shared = ProtoSchemaRegistry()
  }
}
