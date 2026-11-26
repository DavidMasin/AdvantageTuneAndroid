// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpOffset
import okio.ByteString
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.models.hexString
import org.littletonrobotics.advantagetune.store.NTStore

/** The menu shown when a topic is long pressed. */
@Composable
fun TopicContextMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  offset: DpOffset,
  store: NTStore? = null,
  topic: NT4Topic? = null,
  topicName: String? = null,
  value: TopicValue? = null,
  rawData: ByteString? = null,
) {
  val copyToClipboard = rememberClipboardCopy()
  val resolvedName = topic?.name ?: topicName ?: ""

  if (expanded) {
    LaunchedEffect(Unit) { Haptics.impact(Haptics.ImpactStyle.Medium) }
  }

  Menu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
    // Interactive topic actions (if store is provided)
    if (store != null) {
      val resolvedTopic = topic ?: store.topicsByName[resolvedName]
      val isImmutable = resolvedTopic?.isImmutable ?: false

      if (!isImmutable) {
        val zeroValue = resolvedTopic?.let { zeroedValue(value, it) }
        if (resolvedTopic != null && zeroValue != null && value != zeroValue) {
          MenuItem(title = stringResource(R.string.zero), icon = R.drawable.ic_counter_0) {
            onDismiss()
            store.updateValue(resolvedTopic, zeroValue)
          }
        }

        val isPublished = store.isPublished(resolvedName)
        if (isPublished) {
          MenuItem(title = stringResource(R.string.unpublish), icon = R.drawable.ic_dangerous) {
            onDismiss()
            store.unpublishTopic(resolvedName)
          }

          MenuItem(
            title = stringResource(R.string.revert),
            icon = R.drawable.ic_undo,
            isDestructive = true,
          ) {
            onDismiss()
            store.revertAndUnpublishTopic(resolvedName)
          }
        } else if (value != null && resolvedTopic != null) {
          MenuItem(title = stringResource(R.string.publish), icon = R.drawable.ic_arrow_circle_up) {
            onDismiss()
            store.updateValue(resolvedTopic, value)
          }
        }
      }

      HorizontalDivider(color = AppTheme.colors.separator)
    }

    // Clipboard actions
    if (rawData != null) {
      MenuItem(title = stringResource(R.string.copy_value), icon = R.drawable.ic_content_paste) {
        onDismiss()
        copyToClipboard(rawData.hexString)
      }
    } else if (value != null) {
      MenuItem(title = stringResource(R.string.copy_value), icon = R.drawable.ic_content_paste) {
        onDismiss()
        copyToClipboard(value.fullString)
      }
    }

    if (resolvedName.isNotEmpty()) {
      MenuItem(title = stringResource(R.string.copy_key), icon = R.drawable.ic_content_copy) {
        onDismiss()
        copyToClipboard(resolvedName)
      }
    }
  }
}

private fun zeroedValue(value: TopicValue?, topic: NT4Topic): TopicValue? {
  if (value != null) {
    return when {
      value is TopicValue.DoubleValue -> TopicValue.DoubleValue(0.0)
      value is TopicValue.FloatValue -> TopicValue.FloatValue(0f)
      value is TopicValue.IntValue -> TopicValue.IntValue(0)
      value is TopicValue.DoubleArrayValue && value.value.isNotEmpty() ->
        TopicValue.DoubleArrayValue(List(value.value.size) { 0.0 })
      value is TopicValue.FloatArrayValue && value.value.isNotEmpty() ->
        TopicValue.FloatArrayValue(List(value.value.size) { 0f })
      value is TopicValue.IntArrayValue && value.value.isNotEmpty() ->
        TopicValue.IntArrayValue(List(value.value.size) { 0L })
      else -> null
    }
  }

  return when (topic.type.lowercase()) {
    "double" -> TopicValue.DoubleValue(0.0)
    "float" -> TopicValue.FloatValue(0f)
    "int" -> TopicValue.IntValue(0)
    else -> null
  }
}
