// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.models

import org.json.JSONArray
import org.json.JSONObject

object NT4Constants {
  const val defaultHost = "robot.local"
  const val defaultPort = 5810
  const val appName = "AdvantageTune"
  const val subprotocolV41 = "v4.1.networktables.first.wpi.edu"
  const val subprotocolV40 = "networktables.first.wpi.edu"
  const val subprotocolRTT = "rtt.networktables.first.wpi.edu"
  const val defaultSubprotocols = "$subprotocolV41, $subprotocolV40"
  const val subprotocol = defaultSubprotocols

  val typeIndexLookup: Map<String, Int> =
    mapOf(
      "boolean" to 0,
      "double" to 1,
      "int" to 2,
      "float" to 3,
      "string" to 4,
      "json" to 4,
      "raw" to 5,
      "rpc" to 5,
      "msgpack" to 5,
      "protobuf" to 5,
      "boolean[]" to 16,
      "double[]" to 17,
      "int[]" to 18,
      "float[]" to 19,
      "string[]" to 20,
    )

  fun typeIndex(typeString: String): Int {
    return typeIndexLookup[typeString] ?: 5 // Default to binary / raw
  }
}

data class NT4SubscriptionOptions(
  val periodic: Double = 0.02, // 50 Hz default
  val all: Boolean = false,
  val topicsonly: Boolean = false,
  val prefix: Boolean = false,
)

data class NT4Topic(
  val id: Int, // Assigned by server ("id") or publisher ("pubuid")
  val name: String,
  val type: String,
  val properties: Map<String, String> = emptyMap(),
  val pubuid: Int? = null,
) {
  val typeIndex: Int
    get() = NT4Constants.typeIndex(type)

  val isSchema: Boolean
    get() =
      name.startsWith("/.schema") || name.startsWith(".schema") || name.contains("/.schema") ||
        name.contains(".schema") || name.startsWith("/_") || name.startsWith("/$") ||
        name.startsWith("$")

  val isImmutable: Boolean
    get() {
      // Table "AdvantageKit" check
      val cleanName = if (name.startsWith("/")) name.substring(1) else name
      if (cleanName == "AdvantageKit" || cleanName.startsWith("AdvantageKit/")) {
        return true
      }

      // Property check for "mutable"
      val mutableVal = properties["mutable"]?.trim()?.lowercase()
      if (mutableVal != null) {
        if (mutableVal == "false" || mutableVal == "0") {
          return true
        }
        if (mutableVal == "true" || mutableVal == "1") {
          return false
        }
      }

      return false
    }

  val isMutable: Boolean
    get() = !isImmutable

  val isStruct: Boolean
    get() = type.startsWith("struct:")

  val isProtobuf: Boolean
    get() = type.startsWith("proto:")

  val isRaw: Boolean
    get() =
      typeIndex == 5 || isStruct || isProtobuf || type == "raw" || type == "binary" ||
        type == "msgpack" || type == "rpc"

  val isArray: Boolean
    get() = typeIndex in 16..20 || type.contains("[]")
}

// MARK: - JSON Protocol Wire Messages

sealed class NT4ClientMessage {
  data class Publish(
    val name: String,
    val type: String,
    val pubuid: Int,
    val properties: Map<String, String>,
  ) : NT4ClientMessage()

  data class Unpublish(val pubuid: Int) : NT4ClientMessage()

  data class SetProperties(val name: String, val update: Map<String, String>) : NT4ClientMessage()

  data class Subscribe(
    val topics: List<String>,
    val subuid: Int,
    val options: NT4SubscriptionOptions,
  ) : NT4ClientMessage()

  data class Unsubscribe(val subuid: Int) : NT4ClientMessage()

  fun toJSONString(): String? {
    return try {
      val dict = JSONObject()
      when (this) {
        is Publish -> {
          dict.put("method", "publish")
          dict.put(
            "params",
            JSONObject()
              .put("name", name)
              .put("type", type)
              .put("pubuid", pubuid)
              .put("properties", JSONObject(properties)),
          )
        }

        is Unpublish -> {
          dict.put("method", "unpublish")
          dict.put("params", JSONObject().put("pubuid", pubuid))
        }

        is SetProperties -> {
          dict.put("method", "setproperties")
          dict.put("params", JSONObject().put("name", name).put("update", JSONObject(update)))
        }

        is Subscribe -> {
          dict.put("method", "subscribe")
          dict.put(
            "params",
            JSONObject()
              .put("topics", JSONArray(topics))
              .put("subuid", subuid)
              .put(
                "options",
                JSONObject()
                  .put("periodic", options.periodic)
                  .put("all", options.all)
                  .put("topicsonly", options.topicsonly)
                  .put("prefix", options.prefix),
              ),
          )
        }

        is Unsubscribe -> {
          dict.put("method", "unsubscribe")
          dict.put("params", JSONObject().put("subuid", subuid))
        }
      }

      JSONArray().put(dict).toString()
    } catch (e: Exception) {
      null
    }
  }
}
