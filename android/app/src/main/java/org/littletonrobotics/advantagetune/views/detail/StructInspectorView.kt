// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.decoder.StructDecoder
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.DisclosureGroup
import org.littletonrobotics.advantagetune.views.components.LeadingTrailingRow
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.monospaced
import org.littletonrobotics.advantagetune.views.components.weight

@Composable
fun StructInspectorView(
  structValue: StructValue,
  rootTypeName: String,
  modifier: Modifier = Modifier,
  topicPath: String? = null,
  isEditable: Boolean = false,
  onUpdate: ((StructValue) -> Unit)? = null,
) {
  val colors = AppTheme.colors

  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_deployed_code),
        contentDescription = null,
        tint = colors.purple,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = StructDecoder.shared.cleanTypeName(rootTypeName),
        style = AppType.subheadline.weight(FontWeight.SemiBold),
        color = colors.purple,
      )
    }

    Separator()

    StructValueContent(
      value = structValue,
      parentKey = topicPath ?: "",
      isEditable = isEditable,
      onUpdate = onUpdate,
    )
  }
}

@Composable
private fun StructValueContent(
  value: StructValue,
  parentKey: String,
  isEditable: Boolean,
  onUpdate: ((StructValue) -> Unit)?,
) {
  when (value) {
    is StructValue.ObjectValue ->
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        value.fields.forEachIndexed { index, field ->
          key(index, field.first) {
            val currentPath = if (parentKey.isEmpty()) field.first else "$parentKey.${field.first}"
            StructFieldRow(
              key = field.first,
              path = currentPath,
              value = field.second,
              isEditable = isEditable,
            ) { newChildVal ->
              val newFields = value.fields.toMutableList()
              newFields[index] = field.first to newChildVal
              onUpdate?.invoke(StructValue.ObjectValue(newFields))
            }
          }
        }
      }

    is StructValue.ArrayValue ->
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        value.elements.forEachIndexed { index, element ->
          key(index) {
            val currentPath = "$parentKey[$index]"
            StructFieldRow(
              key = "[$index]",
              path = currentPath,
              value = element,
              isEditable = isEditable,
            ) { newChildVal ->
              val newElements = value.elements.toMutableList()
              newElements[index] = newChildVal
              onUpdate?.invoke(StructValue.ArrayValue(newElements))
            }
          }
        }
      }

    else ->
      if (isEditable) {
        StructLeafEditor(
          value = value,
          onCommit = { newLeaf -> onUpdate?.invoke(newLeaf) },
          modifier = Modifier.padding(vertical = 2.dp),
          label = parentKey.ifEmpty { null },
          topicPath = parentKey,
        )
      } else {
        Text(
          text = value.formattedSummary,
          style = AppType.body.monospaced().weight(FontWeight.SemiBold),
          color = AppTheme.colors.label,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
      }
  }
}

@Composable
private fun StructFieldRow(
  key: String,
  path: String,
  value: StructValue,
  isEditable: Boolean = false,
  onUpdate: ((StructValue) -> Unit)? = null,
) {
  val colors = AppTheme.colors
  var isExpanded by remember { mutableStateOf(true) }

  when (value) {
    is StructValue.ObjectValue ->
      DisclosureGroup(
        isExpanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        label = {
          GroupLabel(
            key = key,
            count = "{${Localized.count(value.fields.size)}}",
          )
        },
      ) {
        Column(
          modifier = Modifier.padding(start = 12.dp, top = 6.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          value.fields.forEachIndexed { index, child ->
            key(index, child.first) {
              val childPath = "$path.${child.first}"
              StructFieldRow(
                key = child.first,
                path = childPath,
                value = child.second,
                isEditable = isEditable,
              ) { newChildVal ->
                val newFields = value.fields.toMutableList()
                newFields[index] = child.first to newChildVal
                onUpdate?.invoke(StructValue.ObjectValue(newFields))
              }
            }
          }
        }
      }

    is StructValue.ArrayValue ->
      DisclosureGroup(
        isExpanded = isExpanded,
        onExpandedChange = { isExpanded = it },
        label = {
          GroupLabel(
            key = key,
            count = "[${Localized.count(value.elements.size)}]",
          )
        },
      ) {
        Column(
          modifier = Modifier.padding(start = 12.dp, top = 6.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          value.elements.forEachIndexed { index, child ->
            key(index) {
              val childPath = "$path[$index]"
              StructFieldRow(
                key = "[$index]",
                path = childPath,
                value = child,
                isEditable = isEditable,
              ) { newChildVal ->
                val newElements = value.elements.toMutableList()
                newElements[index] = newChildVal
                onUpdate?.invoke(StructValue.ArrayValue(newElements))
              }
            }
          }
        }
      }

    else ->
      if (isEditable) {
        StructLeafEditor(
          value = value,
          onCommit = { newLeaf -> onUpdate?.invoke(newLeaf) },
          modifier = Modifier.padding(vertical = 2.dp),
          label = key,
          topicPath = path,
        )
      } else {
        LeadingTrailingRow(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 32.dp),
          leading = {
            Text(
              text = key,
              style = AppType.subheadline,
              color = colors.secondaryLabel,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          trailing = {
            if (value is StructValue.EnumValue) {
              TextBadge(
                text = value.name,
                style = AppType.subheadline.monospaced().weight(FontWeight.SemiBold),
                color = colors.purple,
                backgroundColor = colors.purple.copy(alpha = 0.12f),
                shape = CircleShape,
                horizontalPadding = 8.dp,
              )
            } else if (value is StructValue.BoolValue) {
              Text(
                text =
                  stringResource(if (value.value) R.string.value_true else R.string.value_false),
                style = AppType.subheadline.monospaced().weight(FontWeight.SemiBold),
                color = if (value.value) colors.accent else colors.secondaryLabel,
              )
            } else {
              Text(
                text = value.formattedSummary,
                style = AppType.subheadline.monospaced().weight(FontWeight.SemiBold),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
        )
      }
  }
}

@Composable
private fun GroupLabel(key: String, count: String) {
  val colors = AppTheme.colors
  LeadingTrailingRow(
    modifier = Modifier.fillMaxWidth(),
    leading = {
      Text(
        text = key,
        style = AppType.subheadline.weight(FontWeight.Medium),
        color = colors.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    trailing = {
      TextBadge(
        text = count,
        style = AppType.caption2.weight(FontWeight.Bold),
        color = colors.purple,
        backgroundColor = colors.purple.copy(alpha = 0.15f),
        shape = CircleShape,
      )
    },
  )
}
