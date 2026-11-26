// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.Localized
import org.littletonrobotics.advantagetune.models.NumberFormatting
import org.littletonrobotics.advantagetune.models.StructValue
import org.littletonrobotics.advantagetune.models.TopicValue
import org.littletonrobotics.advantagetune.models.UserDefaults
import org.littletonrobotics.advantagetune.models.UserDefaultsKeys
import org.littletonrobotics.advantagetune.models.roundedAwayFromZero
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.ListRow
import org.littletonrobotics.advantagetune.views.components.ListSectionScope
import org.littletonrobotics.advantagetune.views.components.Menu
import org.littletonrobotics.advantagetune.views.components.TextBadge
import org.littletonrobotics.advantagetune.views.components.monospaced
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.screenKeyboard
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.weight

// MARK: - Formatted Text Field

/** The text of a field whose value is parsed when editing ends. */
private class FormattedFieldState<T>(
  initialValue: T,
  private val format: (T) -> String,
  private val parse: (String) -> T?,
) {
  private var shownText = format(initialValue)

  val textFieldState = TextFieldState(shownText)

  val text: String
    get() = textFieldState.text.toString()

  /** Replaces the text with the formatted value, discarding edits. */
  fun show(value: T) {
    shownText = format(value)
    if (text != shownText) {
      textFieldState.setTextAndPlaceCursorAtEnd(shownText)
    }
  }

  /** Returns the value entered by the user, or the current value if the text is not valid. */
  fun endEditing(current: T): T {
    val result = if (text != shownText) parse(text) ?: current else current
    show(result)
    return result
  }
}

@Composable
private fun LeafTextField(
  state: TextFieldState,
  placeholder: String,
  keyboardType: KeyboardType,
  onFocusChanged: (Boolean) -> Unit,
  onSubmit: () -> Unit,
) {
  val colors = AppTheme.colors
  val textStyle = AppType.subheadline.monospaced().weight(FontWeight.SemiBold)

  BasicTextField(
    state = state,
    modifier =
      Modifier.fillMaxWidth()
        .onFocusChanged { onFocusChanged(it.isFocused) }
        .screenKeyboard(state, keyboardType, onSubmit),
    textStyle = textStyle.copy(color = colors.label, textAlign = AppType.trailingAlignment),
    lineLimits = TextFieldLineLimits.SingleLine,
    cursorBrush = SolidColor(colors.accent),
    keyboardOptions =
      KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = keyboardType,
        imeAction = ImeAction.Done,
      ),
    onKeyboardAction = { onSubmit() },
    decorator = { innerTextField ->
      Box(contentAlignment = Alignment.CenterEnd) {
        if (state.text.isEmpty()) {
          Text(
            text = placeholder,
            style = textStyle,
            color = colors.tertiaryLabel,
            maxLines = 1,
            textAlign = AppType.trailingAlignment,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        innerTextField()
      }
    },
  )
}

// MARK: - Double & Float Leaf Editor

private class StepOption(val value: Double, val fractionLength: Int)

private val stepOptions =
  listOf(
    StepOption(0.01, 2),
    StepOption(0.1, 1),
    StepOption(1.0, 1),
    StepOption(10.0, 0),
    StepOption(100.0, 0),
  )

private fun formatStepSize(size: Double): String {
  val match = stepOptions.firstOrNull { abs(it.value - size) < 1e-6 }
  if (match != null) {
    return NumberFormatting.format(match.value, fractionLength = match.fractionLength)
  }
  return NumberFormatting.format(size)
}

private fun savedStepSize(topicPath: String?): Double? {
  if (topicPath == null) return null
  val saved = UserDefaults.double(UserDefaultsKeys.stepSize(topicPath)) ?: return null
  return saved.takeIf { step -> stepOptions.any { it.value == step } }
}

private fun initialStepSize(topicPath: String?): Double {
  savedStepSize(topicPath)?.let { return it }
  val global = UserDefaults.double(UserDefaultsKeys.defaultStepSize)
  if (global != null && stepOptions.any { it.value == global }) {
    return global
  }
  return 1.0
}

@Composable
fun DoubleLeafEditor(
  value: Double,
  onCommit: (Double) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  topicPath: String? = null,
  @Suppress("UNUSED_PARAMETER") isFloat: Boolean = false,
  showStepPicker: Boolean = true,
  onDelete: (() -> Unit)? = null,
) {
  var localValue by remember { mutableDoubleStateOf(value) }
  var stepSize by remember { mutableDoubleStateOf(initialStepSize(topicPath)) }
  var isFocused by remember { mutableStateOf(false) }
  val field = remember {
    FormattedFieldState(value, { NumberFormatting.format(it) }, NumberFormatting::parseDouble)
  }
  val focusManager = LocalFocusManager.current
  val currentValue by rememberUpdatedState(value)
  val currentOnCommit by rememberUpdatedState(onCommit)

  fun commitValue(newValue: Double) {
    if (newValue != currentValue) {
      currentOnCommit(newValue)
    }
  }

  fun endEditing() {
    localValue = field.endEditing(localValue)
    commitValue(localValue)
  }

  fun applyStep(delta: Double): Double {
    val raw = localValue + delta
    val precision =
      when (stepSize) {
        0.01 -> 100.0
        0.1 -> 10.0
        1.0 -> 1.0
        10.0 -> 0.1
        100.0 -> 0.01
        else -> 1_000_000.0
      }

    return (raw * precision).roundedAwayFromZero() / precision
  }

  fun step(delta: Double) {
    Haptics.impact(Haptics.ImpactStyle.Light)
    localValue = applyStep(delta)
    field.show(localValue)
    commitValue(localValue)
  }

  LaunchedEffect(value) {
    if (!isFocused) {
      localValue = value
      field.show(value)
    }
  }

  LaunchedEffect(topicPath) { savedStepSize(topicPath)?.let { stepSize = it } }

  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LeafEditorContainer(
      label = label,
      isFocused = isFocused,
      onDelete = onDelete,
      onCommit = {
        endEditing()
        focusManager.clearFocus()
      },
    ) {
      LeafTextField(
        state = field.textFieldState,
        placeholder = "0.0",
        keyboardType = KeyboardType.Ascii,
        onFocusChanged = { focused ->
          val wasFocused = isFocused
          isFocused = focused
          if (wasFocused && !focused) {
            endEditing()
          }
        },
        onSubmit = {
          endEditing()
          focusManager.clearFocus()
        },
      )
    }

    // Stepper Buttons
    val formattedStep = formatStepSize(stepSize)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StepperButton(text = "-$formattedStep") { step(-stepSize) }
      StepperButton(text = "+$formattedStep") { step(stepSize) }
    }

    // Step Size Selector
    if (showStepPicker) {
      SegmentedPicker(
        label = stringResource(R.string.step_size),
        options = stepOptions.map { formatStepSize(it.value) },
        selectedIndex = stepOptions.indexOfFirst { it.value == stepSize },
        onSelect = { index ->
          val newStep = stepOptions[index].value
          if (stepSize != newStep) {
            Haptics.selection()
          }
          stepSize = newStep
          if (topicPath != null) {
            UserDefaults.set(newStep, UserDefaultsKeys.stepSize(topicPath))
          }
          UserDefaults.set(newStep, UserDefaultsKeys.defaultStepSize)
        },
      )
    }
  }
}

@Composable
private fun RowScope.StepperButton(text: String, onClick: () -> Unit) {
  val colors = AppTheme.colors
  Box(
    modifier =
      Modifier.weight(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(colors.tertiarySystemFill)
        .clickable(onClick = onClick)
        .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = AppType.subheadline.weight(FontWeight.SemiBold),
      color = colors.accent,
      maxLines = 1,
    )
  }
}

/** A row of mutually exclusive options. */
@Composable
private fun SegmentedPicker(
  label: String,
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = AppTheme.colors
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(9.dp))
        .background(colors.tertiarySystemFill)
        .padding(2.dp)
        .semantics { contentDescription = label }
  ) {
    options.forEachIndexed { index, option ->
      val isSelected = index == selectedIndex
      Box(
        modifier =
          Modifier.weight(1f)
            .then(
              if (isSelected) {
                Modifier.shadow(2.dp, RoundedCornerShape(7.dp))
                  .background(colors.segmentedThumb, RoundedCornerShape(7.dp))
              } else {
                Modifier
              }
            )
            .clip(RoundedCornerShape(7.dp))
            .clickable { onSelect(index) }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = option,
          style =
            AppType.footnote.weight(if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
          color = colors.label,
          maxLines = 1,
        )
      }
    }
  }
}

// MARK: - Integer Leaf Editor

@Composable
fun IntLeafEditor(
  value: Long,
  onCommit: (Long) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  isUnsigned: Boolean = false,
  onDelete: (() -> Unit)? = null,
) {
  var localValue by remember { mutableLongStateOf(value) }
  var isFocused by remember { mutableStateOf(false) }
  val field = remember {
    FormattedFieldState(value, { NumberFormatting.format(it) }, NumberFormatting::parseLong)
  }
  val focusManager = LocalFocusManager.current
  val currentValue by rememberUpdatedState(value)
  val currentOnCommit by rememberUpdatedState(onCommit)

  fun commitValue(newValue: Long) {
    if (newValue != currentValue) {
      currentOnCommit(newValue)
    }
  }

  fun endEditing() {
    localValue = field.endEditing(localValue)
    commitValue(localValue)
  }

  LaunchedEffect(value) {
    if (!isFocused) {
      localValue = value
      field.show(value)
    }
  }

  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LeafEditorContainer(
      label = label,
      isFocused = isFocused,
      onDelete = onDelete,
      onCommit = {
        endEditing()
        focusManager.clearFocus()
      },
    ) {
      LeafTextField(
        state = field.textFieldState,
        placeholder = "0",
        keyboardType = if (isUnsigned) KeyboardType.Number else KeyboardType.Ascii,
        onFocusChanged = { focused ->
          val wasFocused = isFocused
          isFocused = focused
          if (wasFocused && !focused) {
            endEditing()
          }
        },
        onSubmit = {
          endEditing()
          focusManager.clearFocus()
        },
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StepperButton(text = "-1") {
        if (!isUnsigned || localValue > 0) {
          Haptics.impact(Haptics.ImpactStyle.Light)
          localValue -= 1
          field.show(localValue)
          commitValue(localValue)
        }
      }

      StepperButton(text = "+1") {
        Haptics.impact(Haptics.ImpactStyle.Light)
        localValue += 1
        field.show(localValue)
        commitValue(localValue)
      }
    }
  }
}

// MARK: - Boolean Leaf Editor

@Composable
fun BoolLeafEditor(
  value: Boolean,
  onCommit: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  onDelete: (() -> Unit)? = null,
) {
  val colors = AppTheme.colors
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 38.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label ?: "",
      style = AppType.body,
      color = colors.secondaryLabel,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = AppType.leadingAlignment,
      modifier = Modifier.weight(1f),
    )

    Switch(
      checked = value,
      onCheckedChange = { onCommit(it) },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = colors.accent,
          checkedBorderColor = Color.Transparent,
          uncheckedThumbColor = Color.White,
          uncheckedTrackColor = colors.systemGray5,
          uncheckedBorderColor = Color.Transparent,
        ),
    )

    if (onDelete != null) {
      DeleteButton(onDelete = onDelete)
    }
  }
}

// MARK: - String Leaf Editor

@Composable
fun StringLeafEditor(
  value: String,
  onCommit: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  onDelete: (() -> Unit)? = null,
) {
  var isFocused by remember { mutableStateOf(false) }
  val field = remember { FormattedFieldState<String>(value, { it }, { it }) }
  val focusManager = LocalFocusManager.current
  val currentValue by rememberUpdatedState(value)
  val currentOnCommit by rememberUpdatedState(onCommit)

  fun commit() {
    val localValue = field.text
    if (localValue != currentValue) {
      currentOnCommit(localValue)
    }
  }

  LaunchedEffect(value) {
    if (!isFocused) {
      field.show(value)
    }
  }

  LeafEditorContainer(
    label = label,
    isFocused = isFocused,
    onDelete = onDelete,
    onCommit = {
      commit()
      focusManager.clearFocus()
    },
    modifier = modifier.padding(vertical = 4.dp),
  ) {
    LeafTextField(
      state = field.textFieldState,
      placeholder = stringResource(R.string.value),
      keyboardType = KeyboardType.Text,
      onFocusChanged = { focused ->
        val wasFocused = isFocused
        isFocused = focused
        if (wasFocused && !focused) {
          commit()
        }
      },
      onSubmit = {
        commit()
        focusManager.clearFocus()
      },
    )
  }
}

// MARK: - Enum Menu Button

@Composable
private fun EnumMenuButton(
  title: String,
  options: List<Pair<Long, String>>,
  selectedKey: Long,
  onSelect: (String, Long) -> Unit,
) {
  val colors = AppTheme.colors
  var expanded by remember { mutableStateOf(false) }

  Box {
    Row(
      modifier =
        Modifier.clip(CircleShape)
          .background(colors.purple.copy(alpha = 0.15f))
          .clickable { expanded = true }
          .padding(horizontal = 10.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = AppType.system(size = 13, weight = FontWeight.SemiBold).monospaced(),
        color = colors.purple,
        maxLines = 1,
      )
      Icon(
        painter = painterResource(R.drawable.ic_unfold_more),
        contentDescription = null,
        tint = colors.purple,
        modifier = Modifier.size(14.dp),
      )
    }

    Menu(expanded = expanded, onDismissRequest = { expanded = false }) {
      for ((key, name) in options) {
        DropdownMenuItem(
          text = { Text(text = "$name ($key)", style = AppType.body, color = colors.label) },
          leadingIcon = {
            if (key == selectedKey) {
              Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(20.dp),
              )
            } else {
              Spacer(modifier = Modifier.width(20.dp))
            }
          },
          onClick = {
            expanded = false
            Haptics.selection()
            onSelect(name, key)
          },
        )
      }
    }
  }
}

// MARK: - Enum Leaf Editor

@Composable
fun EnumLeafEditor(
  name: String,
  rawValue: Long,
  options: Map<Long, String>,
  onCommit: (String, Long) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  onDelete: (() -> Unit)? = null,
) {
  val colors = AppTheme.colors
  val sortedOptions = options.entries.sortedBy { it.key }.map { it.key to it.value }
  val currentDisplayName = options[rawValue] ?: name.ifEmpty { "$rawValue" }

  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 38.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label ?: "",
      style = AppType.body,
      color = colors.secondaryLabel,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = AppType.leadingAlignment,
      modifier = Modifier.weight(1f).padding(end = 20.dp),
    )

    if (options.isNotEmpty()) {
      EnumMenuButton(
        title = currentDisplayName,
        options = sortedOptions,
        selectedKey = rawValue,
        onSelect = onCommit,
      )
    } else {
      TextBadge(
        text = currentDisplayName,
        style = AppType.subheadline.monospaced().weight(FontWeight.SemiBold),
        color = colors.purple,
        backgroundColor = colors.purple.copy(alpha = 0.12f),
        shape = CircleShape,
        horizontalPadding = 10.dp,
        verticalPadding = 6.dp,
      )
    }

    if (onDelete != null) {
      DeleteButton(onDelete = onDelete)
    }
  }
}

// MARK: - Struct Leaf Editor

@Composable
fun StructLeafEditor(
  value: StructValue,
  onCommit: (StructValue) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  topicPath: String? = null,
) {
  when (value) {
    is StructValue.BoolValue ->
      BoolLeafEditor(
        value = value.value,
        onCommit = { onCommit(StructValue.BoolValue(it)) },
        modifier = modifier,
        label = label,
      )

    is StructValue.DoubleValue ->
      DoubleLeafEditor(
        value = value.value,
        onCommit = { onCommit(StructValue.DoubleValue(it)) },
        modifier = modifier,
        label = label,
        topicPath = topicPath,
        isFloat = false,
        showStepPicker = true,
      )

    is StructValue.FloatValue ->
      DoubleLeafEditor(
        value = value.value.toDouble(),
        onCommit = { onCommit(StructValue.FloatValue(it.toFloat())) },
        modifier = modifier,
        label = label,
        topicPath = topicPath,
        isFloat = true,
        showStepPicker = true,
      )

    is StructValue.IntValue ->
      IntLeafEditor(
        value = value.value,
        onCommit = { onCommit(StructValue.IntValue(it)) },
        modifier = modifier,
        label = label,
        isUnsigned = false,
      )

    is StructValue.UIntValue ->
      IntLeafEditor(
        value = value.value.toLong(),
        onCommit = { onCommit(StructValue.UIntValue(maxOf(0L, it).toULong())) },
        modifier = modifier,
        label = label,
        isUnsigned = true,
      )

    is StructValue.StringValue ->
      StringLeafEditor(
        value = value.value,
        onCommit = { onCommit(StructValue.StringValue(it)) },
        modifier = modifier,
        label = label,
      )

    is StructValue.EnumValue ->
      EnumLeafEditor(
        name = value.name,
        rawValue = value.rawValue,
        options = value.options,
        onCommit = { newName, newRaw ->
          onCommit(StructValue.EnumValue(name = newName, rawValue = newRaw, options = value.options))
        },
        modifier = modifier,
        label = label,
      )

    else -> {}
  }
}

// MARK: - Array Editor

/** Adds a row for each element of an array, followed by a row that appends an element. */
private fun <Element> ListSectionScope.arrayEditorRows(
  elements: List<Element>,
  defaultValue: Element,
  onUpdate: (List<Element>) -> Unit,
  editor:
    @Composable
    (index: Int, item: Element, commit: (Element) -> Unit, delete: () -> Unit) -> Unit,
) {
  elements.forEachIndexed { index, item ->
    row(key = index, contentType = "element") { position ->
      ListRow(position = position) {
        Box(modifier = Modifier.padding(vertical = 2.dp)) {
          editor(
            index,
            item,
            { newValue ->
              if (index < elements.size) {
                onUpdate(elements.toMutableList().also { it[index] = newValue })
              }
            },
            {
              if (index < elements.size) {
                onUpdate(elements.toMutableList().also { it.removeAt(index) })
              }
            },
          )
        }
      }
    }
  }

  row(key = "add", contentType = "add") { position ->
    ListRow(
      position = position,
      onClick = {
        Haptics.impact(Haptics.ImpactStyle.Light)
        onUpdate(elements + defaultValue)
      },
    ) {
      Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_add_circle_fill),
          contentDescription = null,
          tint = AppTheme.colors.accent,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.add_element),
          style = AppType.subheadline.weight(FontWeight.SemiBold),
          color = AppTheme.colors.accent,
        )
      }
    }
  }
}

private fun indexLabel(index: Int): String = "[${Localized.count(index)}]"

// MARK: - Topic Array Editor

/** Adds the rows that edit the elements of an array topic. */
fun ListSectionScope.topicArrayEditorRows(
  topicName: String,
  value: TopicValue,
  onCommit: (TopicValue) -> Unit,
) {
  when (value) {
    is TopicValue.DoubleArrayValue ->
      arrayEditorRows(
        elements = value.value,
        defaultValue = 0.0,
        onUpdate = { onCommit(TopicValue.DoubleArrayValue(it)) },
      ) { index, item, commit, delete ->
        DoubleLeafEditor(
          value = item,
          onCommit = commit,
          label = indexLabel(index),
          topicPath = "$topicName[$index]",
          isFloat = false,
          showStepPicker = true,
          onDelete = delete,
        )
      }

    is TopicValue.FloatArrayValue ->
      arrayEditorRows(
        elements = value.value,
        defaultValue = 0.0f,
        onUpdate = { onCommit(TopicValue.FloatArrayValue(it)) },
      ) { index, item, commit, delete ->
        DoubleLeafEditor(
          value = item.toDouble(),
          onCommit = { commit(it.toFloat()) },
          label = indexLabel(index),
          topicPath = "$topicName[$index]",
          isFloat = true,
          showStepPicker = true,
          onDelete = delete,
        )
      }

    is TopicValue.IntArrayValue ->
      arrayEditorRows(
        elements = value.value,
        defaultValue = 0L,
        onUpdate = { onCommit(TopicValue.IntArrayValue(it)) },
      ) { index, item, commit, delete ->
        IntLeafEditor(value = item, onCommit = commit, label = indexLabel(index), onDelete = delete)
      }

    is TopicValue.BooleanArrayValue ->
      arrayEditorRows(
        elements = value.value,
        defaultValue = false,
        onUpdate = { onCommit(TopicValue.BooleanArrayValue(it)) },
      ) { index, item, commit, delete ->
        BoolLeafEditor(value = item, onCommit = commit, label = indexLabel(index), onDelete = delete)
      }

    is TopicValue.StringArrayValue ->
      arrayEditorRows(
        elements = value.value,
        defaultValue = "",
        onUpdate = { onCommit(TopicValue.StringArrayValue(it)) },
      ) { index, item, commit, delete ->
        StringLeafEditor(
          value = item,
          onCommit = commit,
          label = indexLabel(index),
          onDelete = delete,
        )
      }

    else ->
      row(key = "unsupported") { position ->
        ListRow(position = position) {
          Text(
            text = stringResource(R.string.unsupported_array_format),
            style = AppType.body,
            color = AppTheme.colors.secondaryLabel,
          )
        }
      }
  }
}
