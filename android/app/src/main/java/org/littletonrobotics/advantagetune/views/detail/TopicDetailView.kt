// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import okio.ByteString
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.decoder.ProtoDecoder
import org.littletonrobotics.advantagetune.decoder.StructDecoder
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.ConnectionStatusPill
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.InlineTitleBar
import org.littletonrobotics.advantagetune.views.components.InsetGroupedList
import org.littletonrobotics.advantagetune.views.components.LeadingTrailingRow
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.RowPosition
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.TopicContextMenu
import org.littletonrobotics.advantagetune.views.components.TypeBadgeView
import org.littletonrobotics.advantagetune.views.components.monospaced
import org.littletonrobotics.advantagetune.views.components.section
import org.littletonrobotics.advantagetune.views.components.weight

/** Local primitive editing state */
private class TopicDetailState {
  var boolValue by mutableStateOf(false)
  var textValue by mutableStateOf("")
  var doubleValue by mutableDoubleStateOf(0.0)
  var intValue by mutableLongStateOf(0L)
  var localStructValue by mutableStateOf<StructValue?>(null)
  var hasInitialized by mutableStateOf(false)

  fun syncLocalStateWithLatest(store: NTStore, topic: NT4Topic) {
    val value = store.tunedEntries[topic.name]?.currentValue ?: store.latestValues[topic.name]
    if (value == null) return
    hasInitialized = true
    when (value) {
      is TopicValue.BooleanValue -> boolValue = value.value
      is TopicValue.DoubleValue -> doubleValue = value.value
      is TopicValue.FloatValue -> doubleValue = value.value.toDouble()
      is TopicValue.IntValue -> intValue = value.value
      is TopicValue.StringValue -> textValue = value.value
      is TopicValue.RawValue -> textValue = ""
      is TopicValue.Struct -> localStructValue = value.value
      else -> textValue = value.formattedString
    }
  }
}

@Composable
fun TopicDetailView(store: NTStore, topic: NT4Topic, onBack: () -> Unit) {
  val colors = AppTheme.colors
  val state =
    remember(topic.name) { TopicDetailState().apply { syncLocalStateWithLatest(store, topic) } }

  val isPublished = store.isPublished(topic.name)
  val latestValue = store.latestValues[topic.name]
  val isConnected = store.connectionStatus.isConnected

  val isRawTopic = topic.isRaw
  val isDecodedStruct = latestValue is TopicValue.Struct

  val currentStructTypeName: String? =
    if (latestValue is TopicValue.Struct) {
      latestValue.typeName
    } else if (topic.type.startsWith("struct:") || topic.type.startsWith("proto:")) {
      topic.type
    } else {
      null
    }

  val currentLatestStruct: StructValue? = (latestValue as? TopicValue.Struct)?.value

  val isPrimitiveArrayTopic =
    topic.isArray && !topic.type.startsWith("struct:") && !topic.type.startsWith("proto:")

  val isCompositeValue = isDecodedStruct || isPrimitiveArrayTopic

  val latestValueData: ByteString? =
    when (latestValue) {
      is TopicValue.RawValue -> latestValue.data
      is TopicValue.Struct -> latestValue.raw
      is TopicValue.ProtoRaw -> latestValue.data
      else -> null
    }

  fun applyUpdate(newValue: TopicValue) {
    store.updateValue(topic, newValue)
  }

  DisposableEffect(topic.name) {
    store.acquireSubscription(topic.name)
    onDispose { store.releaseSubscription(topic.name) }
  }

  LaunchedEffect(latestValue) {
    if (!state.hasInitialized || (!isPublished && state.localStructValue == null)) {
      state.syncLocalStateWithLatest(store, topic)
    }
  }

  // Strings used by the list
  val topicDetailsHeader = stringResource(R.string.topic_details)
  val liveTelemetryHeader = stringResource(R.string.live_telemetry)
  val structFieldsHeader =
    stringResource(
      if (topic.type.startsWith("proto:") || topic.isProtobuf) {
        R.string.protobuf_fields
      } else {
        R.string.struct_fields
      }
    )
  val structFieldsFooter = stringResource(R.string.subfield_edits_packed)
  val binaryTelemetryHeader = stringResource(R.string.binary_telemetry)
  val editArrayHeader = stringResource(R.string.edit_array_elements)
  val editValueHeader = stringResource(R.string.edit_value)
  val changesFooter = stringResource(R.string.changes_published_automatically)

  Column(modifier = Modifier.fillMaxSize().background(colors.systemGroupedBackground)) {
    InlineTitleBar(
      title = topic.name.split("/").lastOrNull { it.isNotEmpty() } ?: topic.name,
      onBack = onBack,
    ) {
      if (!isConnected) {
        ConnectionStatusPill(store = store)
      }
    }

    InsetGroupedList {
      // MARK: - Metadata
      section(key = "details", header = topicDetailsHeader) {
        row { position ->
          ListRow(
            position = position,
            contextMenu = { expanded, onDismiss, offset ->
              TopicContextMenu(
                expanded,
                onDismiss,
                offset,
                topicName = topic.name,
                value = latestValue,
              )
            },
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadgeView(typeName = topic.type, compact = false)
                Spacer(modifier = Modifier.weight(1f))
                if (topic.isImmutable) {
                  TextBadge(
                    text = stringResource(R.string.read_only),
                    style = AppType.caption2.weight(FontWeight.Bold),
                    color = colors.secondaryLabel,
                    backgroundColor = colors.gray.copy(alpha = 0.2f),
                    shape = CircleShape,
                  )
                } else if (isPublished) {
                  TextBadge(
                    text = stringResource(R.string.published),
                    style = AppType.caption2.weight(FontWeight.Bold),
                    color = colors.orange,
                    backgroundColor = colors.orange.copy(alpha = 0.2f),
                    shape = CircleShape,
                  )
                }
              }

              Text(
                text = topic.name,
                style = AppType.headline,
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // MARK: - Live Telemetry
      if (!isRawTopic || isDecodedStruct) {
        section(key = "telemetry", header = liveTelemetryHeader) {
          row { position ->
            ListRow(
              position = position,
              contextMenu = { expanded, onDismiss, offset ->
                TopicContextMenu(
                  expanded,
                  onDismiss,
                  offset,
                  topicName = topic.name,
                  value = latestValue,
                )
              },
            ) {
              val valueColor = if (isConnected) colors.label else colors.secondaryLabel
              if (isCompositeValue) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = stringResource(R.string.server_value),
                    style = AppType.subheadline.weight(FontWeight.Medium),
                    color = colors.secondaryLabel,
                  )

                  if (latestValue != null) {
                    Text(
                      text = latestValue.formattedString,
                      style = AppType.subheadline.monospaced(),
                      color = valueColor,
                      maxLines = 8,
                      overflow = TextOverflow.Ellipsis,
                    )
                  } else {
                    WaitingText(stringResource(R.string.waiting_for_value))
                  }
                }
              } else {
                LeadingTrailingRow(
                  modifier = Modifier.fillMaxWidth(),
                  leading = {
                    Text(
                      text = stringResource(R.string.server_value),
                      style = AppType.body,
                      color = colors.secondaryLabel,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  trailing = {
                    if (latestValue != null) {
                      Text(
                        text = latestValue.formattedString,
                        style = AppType.body.monospaced().weight(FontWeight.Bold),
                        color = valueColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = AppType.trailingAlignment,
                      )
                    } else {
                      WaitingText(stringResource(R.string.waiting_for_value))
                    }
                  },
                )
              }
            }
          }
        }
      }

      // MARK: - Struct Inspector
      val activeStruct =
        if (topic.isImmutable) currentLatestStruct else (state.localStructValue ?: currentLatestStruct)
      if (activeStruct != null && currentStructTypeName != null) {
        section(
          key = "struct",
          header = structFieldsHeader,
          footer = if (!topic.isImmutable) structFieldsFooter else null,
        ) {
          row { position ->
            ListRow(position = position) {
              StructInspectorView(
                structValue = activeStruct,
                rootTypeName = currentStructTypeName,
                topicPath = topic.name,
                isEditable = !topic.isImmutable,
              ) { updatedStruct ->
                state.localStructValue = updatedStruct
                val encodedData =
                  if (topic.type.startsWith("proto:") || topic.isProtobuf) {
                    ProtoDecoder.shared.encode(typeName = currentStructTypeName, value = updatedStruct)
                  } else {
                    StructDecoder.shared.encode(typeName = currentStructTypeName, value = updatedStruct)
                  }
                if (encodedData != null) {
                  applyUpdate(
                    TopicValue.Struct(
                      typeName = currentStructTypeName,
                      value = updatedStruct,
                      raw = encodedData,
                    )
                  )
                }
              }
            }
          }
        }
      }

      // MARK: - Binary Telemetry
      if (isRawTopic) {
        section(key = "binary", header = binaryTelemetryHeader) {
          row { position ->
            RawTelemetryView(
              topic = topic,
              rawData = latestValueData,
              position = position,
              fallbackValue = latestValue,
              isConnected = isConnected,
            )
          }
        }
      }

      // MARK: - Primitive Array Editor
      if (isPrimitiveArrayTopic && !topic.isImmutable) {
        section(key = "array", header = editArrayHeader, footer = changesFooter) {
          val value = store.tunedEntries[topic.name]?.currentValue ?: latestValue
          if (value != null) {
            topicArrayEditorRows(topicName = topic.name, value = value, onCommit = ::applyUpdate)
          } else {
            row(key = "waiting") { position ->
              ListRow(position = position) {
                WaitingText(stringResource(R.string.waiting_for_array_data))
              }
            }
          }
        }
      }

      // MARK: - Primitive Value Editor
      if (
        !topic.isImmutable && !topic.isArray && !isRawTopic && !topic.type.startsWith("struct:") &&
          !topic.type.startsWith("proto:")
      ) {
        section(key = "value", header = editValueHeader, footer = changesFooter) {
          row { position ->
            ListRow(position = position) {
              PrimitiveEditorControls(topic = topic, state = state, applyUpdate = ::applyUpdate)
            }
          }
        }
      }

      // MARK: - Actions Section
      if (isPublished && !topic.isImmutable) {
        section(key = "actions") {
          row(key = "unpublish") { position ->
            ActionRow(
              position = position,
              title = stringResource(R.string.unpublish),
              icon = R.drawable.ic_dangerous,
              color = colors.accent,
            ) {
              Haptics.impact(Haptics.ImpactStyle.Medium)
              store.unpublishTopic(topic.name)
            }
          }

          row(key = "revert") { position ->
            ActionRow(
              position = position,
              title = stringResource(R.string.revert),
              icon = R.drawable.ic_undo,
              color = colors.red,
            ) {
              Haptics.notification(Haptics.NotificationType.Warning)
              store.revertAndUnpublishTopic(topic.name)
              state.localStructValue = null
              state.syncLocalStateWithLatest(store, topic)
            }
          }
        }
      }
    }
  }
}

// MARK: - Primitive Value Editor

@Composable
private fun PrimitiveEditorControls(
  topic: NT4Topic,
  state: TopicDetailState,
  applyUpdate: (TopicValue) -> Unit,
) {
  val type = topic.type.lowercase()
  val label = stringResource(R.string.local_value)

  if (type == "boolean") {
    BoolLeafEditor(
      value = state.boolValue,
      onCommit = { newBool ->
        state.boolValue = newBool
        applyUpdate(TopicValue.BooleanValue(newBool))
      },
      label = label,
    )
  } else if (type == "double") {
    DoubleLeafEditor(
      value = state.doubleValue,
      onCommit = { newDouble ->
        state.doubleValue = newDouble
        applyUpdate(TopicValue.DoubleValue(newDouble))
      },
      label = label,
      topicPath = topic.name,
      isFloat = false,
    )
  } else if (type == "float") {
    DoubleLeafEditor(
      value = state.doubleValue,
      onCommit = { newDouble ->
        state.doubleValue = newDouble
        applyUpdate(TopicValue.FloatValue(newDouble.toFloat()))
      },
      label = label,
      topicPath = topic.name,
      isFloat = true,
    )
  } else if (type == "int") {
    IntLeafEditor(
      value = state.intValue,
      onCommit = { newInt ->
        state.intValue = newInt
        applyUpdate(TopicValue.IntValue(newInt))
      },
      label = label,
      isUnsigned = false,
    )
  } else {
    StringLeafEditor(
      value = state.textValue,
      onCommit = { newText ->
        state.textValue = newText
        applyUpdate(TopicValue.StringValue(newText))
      },
      label = label,
    )
  }
}

@Composable
private fun WaitingText(text: String) {
  Text(
    text = text,
    style = AppType.body,
    fontStyle = FontStyle.Italic,
    color = AppTheme.colors.secondaryLabel,
  )
}

/** A row that acts as a button, with its label centered. */
@Composable
private fun ActionRow(
  position: RowPosition,
  title: String,
  @DrawableRes icon: Int,
  color: Color,
  onClick: () -> Unit,
) {
  ListRow(position = position, separatorInset = 0.dp, onClick = onClick) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(20.dp),
      )
      Text(text = title, style = AppType.body, color = color)
    }
  }
}
