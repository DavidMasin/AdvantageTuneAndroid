// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import org.littletonrobotics.advantagetune.R

// MARK: - Inset Grouped List

/** Where a row sits within its section, which decides the corners that are rounded. */
enum class RowPosition {
  Single,
  First,
  Middle,
  Last,
}

fun rowPosition(index: Int, count: Int): RowPosition {
  return if (count <= 1) {
    RowPosition.Single
  } else if (index == 0) {
    RowPosition.First
  } else if (index == count - 1) {
    RowPosition.Last
  } else {
    RowPosition.Middle
  }
}

private val ListHorizontalMargin = 16.dp
private val RowHorizontalPadding = 16.dp
private val RowCornerRadius = 20.dp

/** The shape of a row at this position: rounded where it meets the edge of its section. */
fun RowPosition.shape(): Shape {
  val radius = RowCornerRadius
  return when (this) {
    RowPosition.Single -> RoundedCornerShape(radius)
    RowPosition.First -> RoundedCornerShape(topStart = radius, topEnd = radius)
    RowPosition.Middle -> RoundedCornerShape(0.dp)
    RowPosition.Last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
  }
}

/** Shows a menu for a row at the given offset from the row's bottom start corner. */
typealias RowContextMenu =
  @Composable (expanded: Boolean, onDismiss: () -> Unit, offset: DpOffset) -> Unit

/** A list with rounded sections on a grouped background. */
@Composable
fun InsetGroupedList(
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  content: LazyListScope.() -> Unit,
) {
  LazyColumn(
    modifier =
      modifier
        .fillMaxSize()
        .background(AppTheme.colors.systemGroupedBackground)
        .dragToScroll(state, Orientation.Vertical),
    state = state,
    contentPadding = PaddingValues(bottom = 24.dp),
    content = content,
  )
}

/** A row of an inset grouped list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
  position: RowPosition,
  modifier: Modifier = Modifier,
  indent: Dp = 0.dp,
  separatorInset: Dp = RowHorizontalPadding,
  showsChevron: Boolean = false,
  isSelected: Boolean = false,
  onClick: (() -> Unit)? = null,
  contextMenu: RowContextMenu? = null,
  content: @Composable () -> Unit,
) {
  val colors = AppTheme.colors
  val density = LocalDensity.current
  var menuExpanded by remember { mutableStateOf(false) }
  var pressPosition by remember { mutableStateOf(Offset.Zero) }
  var rowHeight by remember { mutableIntStateOf(0) }

  var rowModifier =
    modifier
      .fillMaxWidth()
      .padding(horizontal = ListHorizontalMargin)
      .clip(position.shape())
      .background(colors.secondarySystemGroupedBackground)
      .background(if (isSelected) colors.accent.copy(alpha = 0.14f) else Color.Transparent)

  if (contextMenu != null) {
    rowModifier =
      rowModifier
        .onSizeChanged { rowHeight = it.height }
        .onSecondaryClick { position ->
          pressPosition = position
          menuExpanded = true
        }
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressPosition = down.position
          }
        }
  }

  if (onClick != null || contextMenu != null) {
    rowModifier =
      rowModifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = if (onClick != null) ripple() else null,
        onClick = { onClick?.invoke() },
        onLongClick =
          if (contextMenu != null) {
            { menuExpanded = true }
          } else {
            null
          },
      )
  }

  Box(modifier = rowModifier) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 48.dp)
          .padding(
            start = RowHorizontalPadding + indent,
            end = RowHorizontalPadding,
            top = 8.dp,
            bottom = 8.dp,
          ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.weight(1f)) { content() }

      if (showsChevron) {
        DisclosureIndicator(modifier = Modifier.padding(start = 6.dp))
      }
    }

    if (position == RowPosition.First || position == RowPosition.Middle) {
      Separator(
        modifier = Modifier.align(Alignment.BottomStart).padding(start = separatorInset + indent)
      )
    }

    if (contextMenu != null) {
      val offset =
        with(density) { DpOffset(pressPosition.x.toDp(), (pressPosition.y - rowHeight).toDp()) }
      contextMenu(menuExpanded, { menuExpanded = false }, offset)
    }
  }
}

/** The chevron shown by rows that navigate. */
@Composable
fun DisclosureIndicator(modifier: Modifier = Modifier) {
  Icon(
    painter = painterResource(R.drawable.ic_chevron_right),
    contentDescription = null,
    tint = AppTheme.colors.tertiaryLabel,
    modifier = modifier.size(20.dp),
  )
}

@Composable
fun SectionHeader(text: String?, modifier: Modifier = Modifier) {
  if (text == null) {
    Box(modifier = modifier.height(20.dp))
  } else {
    Text(
      text = text,
      style = AppType.subheadline.weight(FontWeight.SemiBold),
      color = AppTheme.colors.secondaryLabel,
      textAlign = AppType.leadingAlignment,
      modifier =
        modifier
          .fillMaxWidth()
          .padding(horizontal = ListHorizontalMargin + RowHorizontalPadding)
          .padding(top = 20.dp, bottom = 8.dp),
    )
  }
}

@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = AppType.footnote,
    color = AppTheme.colors.secondaryLabel,
    textAlign = AppType.leadingAlignment,
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = ListHorizontalMargin + RowHorizontalPadding)
        .padding(top = 8.dp),
  )
}

class ListSectionScope internal constructor() {
  internal class Row(
    val key: Any?,
    val contentType: Any?,
    val content: @Composable (position: RowPosition) -> Unit,
  )

  internal val rows = mutableListOf<Row>()

  /** Adds a row. The content is given its position to pass on to [ListRow]. */
  fun row(
    key: Any? = null,
    contentType: Any? = null,
    content: @Composable (position: RowPosition) -> Unit,
  ) {
    rows.add(Row(key, contentType, content))
  }
}

/** Adds a section of rows with an optional header and footer. */
fun LazyListScope.section(
  key: String,
  header: String? = null,
  footer: String? = null,
  content: ListSectionScope.() -> Unit,
) {
  val rows = ListSectionScope().apply(content).rows

  item(key = "$key/header", contentType = "header") { SectionHeader(header) }

  itemsIndexed(
    items = rows,
    key = { index, row -> "$key/${row.key ?: index}" },
    contentType = { _, row -> row.contentType },
  ) { index, row ->
    row.content(rowPosition(index, rows.size))
  }

  if (footer != null) {
    item(key = "$key/footer", contentType = "footer") { SectionFooter(footer) }
  }
}

// MARK: - Shared Views

/** A hairline separator. */
@Composable
fun Separator(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().height(0.5.dp).background(AppTheme.colors.separator))
}

/** A placeholder for content that is unavailable. */
@Composable
fun ContentUnavailableView(
  title: String,
  @DrawableRes icon: Int,
  description: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      tint = AppTheme.colors.secondaryLabel,
      modifier = Modifier.size(56.dp),
    )
    Text(
      text = title,
      style = AppType.title2.weight(FontWeight.Bold),
      color = AppTheme.colors.label,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = description,
      style = AppType.subheadline,
      color = AppTheme.colors.secondaryLabel,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

/** A small label on a tinted background. */
@Composable
fun TextBadge(
  text: String,
  style: TextStyle,
  color: Color,
  backgroundColor: Color,
  shape: Shape,
  modifier: Modifier = Modifier,
  horizontalPadding: Dp = 6.dp,
  verticalPadding: Dp = 2.dp,
) {
  Text(
    text = text,
    style = style,
    color = color,
    maxLines = 1,
    modifier =
      modifier
        .clip(shape)
        .background(backgroundColor)
        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
  )
}

/** A label that shows or hides its content when tapped. */
@Composable
fun DisclosureGroup(
  isExpanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  label: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 32.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) {
            onExpandedChange(!isExpanded)
          },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.weight(1f)) { label() }
      Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = AppTheme.colors.accent,
        modifier =
          Modifier.padding(start = 8.dp)
            .size(20.dp)
            .rotate(if (!isExpanded) 0f else if (isRtl) -90f else 90f),
      )
    }

    if (isExpanded) {
      content()
    }
  }
}

/**
 * Places content at the leading and trailing edges. When they do not both fit, each side is
 * offered half of the width and the space that one side does not need goes to the other.
 */
@Composable
fun LeadingTrailingRow(
  modifier: Modifier = Modifier,
  spacing: Dp = 8.dp,
  leading: @Composable () -> Unit,
  trailing: @Composable () -> Unit,
) {
  Layout(
    content = {
      Box { leading() }
      Box { trailing() }
    },
    modifier = modifier,
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val available = (width - spacing.roundToPx()).coerceAtLeast(0)
    val loose = constraints.copy(minWidth = 0, minHeight = 0)

    val leadingIdeal = measurables[0].maxIntrinsicWidth(constraints.maxHeight)
    val trailingIdeal = measurables[1].maxIntrinsicWidth(constraints.maxHeight)
    val half = available / 2
    val leadingMax: Int
    val trailingMax: Int
    if (leadingIdeal + trailingIdeal <= available) {
      leadingMax = leadingIdeal
      trailingMax = available - leadingIdeal
    } else if (leadingIdeal <= half) {
      leadingMax = leadingIdeal
      trailingMax = available - leadingIdeal
    } else if (trailingIdeal <= half) {
      trailingMax = trailingIdeal
      leadingMax = available - trailingIdeal
    } else {
      leadingMax = half
      trailingMax = available - half
    }

    val leadingPlaceable = measurables[0].measure(loose.copy(maxWidth = leadingMax))
    val trailingPlaceable = measurables[1].measure(loose.copy(maxWidth = trailingMax))

    val height =
      max(leadingPlaceable.height, trailingPlaceable.height).coerceAtLeast(constraints.minHeight)
    layout(width, height) {
      leadingPlaceable.placeRelative(0, (height - leadingPlaceable.height) / 2)
      trailingPlaceable.placeRelative(
        width - trailingPlaceable.width,
        (height - trailingPlaceable.height) / 2,
      )
    }
  }
}
