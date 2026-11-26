// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NT4Topic
import org.littletonrobotics.advantagetune.store.NTStore
import org.littletonrobotics.advantagetune.views.components.AppTheme
import org.littletonrobotics.advantagetune.views.components.AppType
import org.littletonrobotics.advantagetune.views.components.ConnectionBar
import org.littletonrobotics.advantagetune.views.components.ContentUnavailableView
import org.littletonrobotics.advantagetune.views.components.Haptics
import org.littletonrobotics.advantagetune.views.components.InsetGroupedList
import org.littletonrobotics.advantagetune.views.components.LargeTitleBar
import org.littletonrobotics.advantagetune.views.components.NavigationStack
import org.littletonrobotics.advantagetune.views.components.Navigator
import org.littletonrobotics.advantagetune.views.components.Separator
import org.littletonrobotics.advantagetune.views.components.TopicRowView
import org.littletonrobotics.advantagetune.views.components.dragToScroll
import org.littletonrobotics.advantagetune.views.components.painterResource
import org.littletonrobotics.advantagetune.views.components.screenKeyboard
import org.littletonrobotics.advantagetune.views.components.section
import org.littletonrobotics.advantagetune.views.components.stringResource
import org.littletonrobotics.advantagetune.views.components.weight
import org.littletonrobotics.advantagetune.views.detail.TopicDetailView

enum class FilterOption(val rawValue: String, @StringRes val titleKey: Int) {
  All("All", R.string.filter_all),
  Numeric("Numeric", R.string.filter_numeric),
  Boolean("Boolean", R.string.filter_boolean),
  String("String", R.string.filter_string),
  Arrays("Arrays", R.string.filter_arrays),
  StructProto("Struct/Proto", R.string.filter_struct_proto),
}

/**
 * @param activation Changes each time the search tab is selected, which moves focus to the search
 *   field.
 */
@Composable
fun SearchView(store: NTStore, navigator: Navigator<NT4Topic>, activation: Int = 0) {
  val colors = AppTheme.colors
  val searchFieldState = rememberTextFieldState()
  val searchText = searchFieldState.text.toString()
  var selectedTypeFilter by rememberSaveable { mutableStateOf(FilterOption.All) }
  var handledActivation by rememberSaveable { mutableStateOf(0) }

  NavigationStack(
    navigator = navigator,
    root = {
      val filteredTopics by
        remember(searchText, selectedTypeFilter) {
          derivedStateOf {
            store.searchTopics(
              query = searchText,
              filter = selectedTypeFilter.rawValue,
              limit = 100,
            )
          }
        }
      val resultsHeader = stringResource(R.string.results_count, filteredTopics.size)

      val focusRequester = remember { FocusRequester() }
      LaunchedEffect(activation) {
        if (activation != handledActivation) {
          handledActivation = activation
          focusRequester.requestFocus()
        }
      }

      Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleBar(title = stringResource(R.string.search))

        SearchField(
          state = searchFieldState,
          prompt = stringResource(R.string.search_topics_prompt),
          focusRequester = focusRequester,
          modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )

        // Connection bar
        ConnectionBar(store = store)

        Separator()

        // Filter picker
        val filterScrollState = rememberScrollState()
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(colors.systemBackground)
              .dragToScroll(filterScrollState, Orientation.Horizontal)
              .horizontalScroll(filterScrollState)
              .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (filter in FilterOption.entries) {
            val isSelected = selectedTypeFilter == filter
            Text(
              text = stringResource(filter.titleKey),
              style = AppType.subheadline.weight(FontWeight.Medium),
              color = if (isSelected) Color.White else colors.label,
              maxLines = 1,
              modifier =
                Modifier.clip(CircleShape)
                  .background(if (isSelected) colors.accent else colors.secondarySystemBackground)
                  .clickable {
                    if (selectedTypeFilter != filter) {
                      Haptics.selection()
                      selectedTypeFilter = filter
                    }
                  }
                  .padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }

        Separator()

        // Results
        if (filteredTopics.isEmpty()) {
          if (searchText.isEmpty()) {
            ContentUnavailableView(
              title = stringResource(R.string.search_topics_title),
              icon = R.drawable.ic_search,
              description = stringResource(R.string.find_topic_by_name),
            )
          } else {
            ContentUnavailableView(
              title = stringResource(R.string.no_results_for, searchText),
              icon = R.drawable.ic_search,
              description = stringResource(R.string.check_spelling),
            )
          }
        } else {
          InsetGroupedList {
            section(key = "results", header = resultsHeader) {
              for (topic in filteredTopics) {
                row(key = topic.name, contentType = "topic") { position ->
                  TopicRowView(
                    store = store,
                    topic = topic,
                    position = position,
                    isPublished = store.isPublished(topic.name),
                    onClick = { navigator.push(topic) },
                  )
                }
              }
            }
          }
        }
      }
    },
  ) { topic ->
    TopicDetailView(store = store, topic = topic, onBack = { navigator.pop() })
  }
}

@Composable
private fun SearchField(
  state: TextFieldState,
  prompt: String,
  focusRequester: FocusRequester,
  modifier: Modifier = Modifier,
) {
  val colors = AppTheme.colors
  val focusManager = LocalFocusManager.current

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(colors.tertiarySystemFill)
        .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_search),
      contentDescription = null,
      tint = colors.secondaryLabel,
      modifier = Modifier.size(20.dp),
    )

    BasicTextField(
      state = state,
      modifier =
        Modifier.weight(1f)
          .focusRequester(focusRequester)
          .screenKeyboard(state, KeyboardType.Text) { focusManager.clearFocus() },
      textStyle = AppType.body.copy(color = colors.label, textAlign = AppType.leadingAlignment),
      lineLimits = TextFieldLineLimits.SingleLine,
      cursorBrush = SolidColor(colors.accent),
      keyboardOptions =
        KeyboardOptions(
          capitalization = KeyboardCapitalization.None,
          autoCorrectEnabled = false,
          imeAction = ImeAction.Search,
        ),
      onKeyboardAction = { focusManager.clearFocus() },
      decorator = { innerTextField ->
        Box {
          if (state.text.isEmpty()) {
            Text(
              text = prompt,
              style = AppType.body,
              color = colors.secondaryLabel,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          innerTextField()
        }
      },
    )

    if (state.text.isNotEmpty()) {
      Icon(
        painter = painterResource(R.drawable.ic_cancel_fill),
        contentDescription = stringResource(R.string.clear_text),
        tint = colors.secondaryLabel,
        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { state.clearText() },
      )
    }
  }
}
