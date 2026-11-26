// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

package org.littletonrobotics.advantagetune.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.littletonrobotics.advantagetune.R
import org.littletonrobotics.advantagetune.models.NumberFormatting
import org.littletonrobotics.advantagetune.store.ConnectionStatus
import org.littletonrobotics.advantagetune.store.NTStore

@Composable
fun ConnectionBar(store: NTStore, modifier: Modifier = Modifier) {
  val colors = AppTheme.colors
  val focusManager = LocalFocusManager.current

  // The text field starts from the store's host and writes each edit back to it
  val hostFieldState = remember { TextFieldState(store.serverHost) }

  fun commitHost() {
    val text = hostFieldState.text.toString()
    if (text != store.serverHost) {
      store.serverHost = text
    }
  }

  LaunchedEffect(hostFieldState) {
    snapshotFlow { hostFieldState.text.toString() }.collect { commitHost() }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(colors.systemBackground)
        .padding(horizontal = 16.dp)
        .padding(top = 4.dp, bottom = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Host input field
      Row(
        modifier =
          Modifier.weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.secondarySystemBackground)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_language),
          contentDescription = null,
          tint = colors.secondaryLabel,
          modifier = Modifier.size(18.dp),
        )

        BasicTextField(
          state = hostFieldState,
          modifier = Modifier.weight(1f),
          textStyle = AppType.body.copy(color = colors.label, textAlign = AppType.leadingAlignment),
          lineLimits = TextFieldLineLimits.SingleLine,
          cursorBrush = SolidColor(colors.accent),
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.None,
              autoCorrectEnabled = false,
              keyboardType = KeyboardType.Uri,
              imeAction = ImeAction.Done,
            ),
          onKeyboardAction = {
            focusManager.clearFocus()
            commitHost()
            store.reconnect()
          },
          decorator = { innerTextField ->
            Box {
              if (hostFieldState.text.isEmpty()) {
                Text(
                  text = stringResource(R.string.server_ip_or_hostname),
                  style = AppType.body,
                  color = colors.tertiaryLabel,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              innerTextField()
            }
          },
        )

        if (hostFieldState.text.isNotEmpty()) {
          Icon(
            painter = painterResource(R.drawable.ic_cancel_fill),
            contentDescription = stringResource(R.string.clear_text),
            tint = colors.secondaryLabel,
            modifier =
              Modifier.size(16.dp).clip(CircleShape).clickable {
                hostFieldState.clearText()
                commitHost()
              },
          )
        }
      }

      // Connect / disconnect button
      val isActive = store.isConnectionRequested || store.connectionStatus.isConnected
      val buttonColor = if (isActive) colors.red else colors.accent
      Text(
        text = stringResource(if (isActive) R.string.disconnect else R.string.connect),
        style = AppType.subheadline.weight(FontWeight.SemiBold),
        color = buttonColor,
        maxLines = 1,
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .background(buttonColor.copy(alpha = 0.12f))
            .clickable {
              focusManager.clearFocus()
              commitHost()
              if (store.isConnectionRequested || store.connectionStatus.isConnected) {
                store.disconnect()
              } else {
                store.connect()
              }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
      )
    }

    // Status bar
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(8.dp).clip(CircleShape).background(statusColor(store.connectionStatus))
        )

        Text(
          text = fullStatusText(store.connectionStatus, store.serverHost),
          style = AppType.caption.weight(FontWeight.Medium),
          color = colors.secondaryLabel,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text = stringResource(R.string.port, store.parsedPort.toString()),
        style = AppType.caption2,
        color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.8f),
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

@Composable
private fun statusColor(status: ConnectionStatus): Color {
  return when (status) {
    is ConnectionStatus.Connected -> AppTheme.colors.green
    is ConnectionStatus.Connecting -> AppTheme.colors.yellow
    is ConnectionStatus.Disconnected -> AppTheme.colors.red
  }
}

@Composable
private fun fullStatusText(status: ConnectionStatus, serverHost: String): String {
  return when (status) {
    is ConnectionStatus.Connected ->
      if (status.latencyMs > 0) {
        val formattedLatency =
          NumberFormatting.format(status.latencyMs, fractionLength = 1, grouping = true)
        stringResource(R.string.connected_latency, formattedLatency)
      } else {
        stringResource(R.string.connected)
      }
    is ConnectionStatus.Connecting -> stringResource(R.string.connecting_to, serverHost)
    is ConnectionStatus.Disconnected -> stringResource(R.string.disconnected)
  }
}

@Composable
fun ConnectionStatusPill(store: NTStore, modifier: Modifier = Modifier) {
  val colors = AppTheme.colors
  var expanded by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    Row(
      modifier =
        Modifier.clip(CircleShape)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
          ) {
            expanded = true
          }
          .padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(8.dp).clip(CircleShape).background(statusColor(store.connectionStatus))
      )

      Text(
        text =
          when (store.connectionStatus) {
            is ConnectionStatus.Connected -> stringResource(R.string.connected)
            is ConnectionStatus.Connecting -> stringResource(R.string.connecting)
            is ConnectionStatus.Disconnected -> stringResource(R.string.disconnected)
          },
        style = AppType.caption.weight(FontWeight.SemiBold),
        color =
          if (store.connectionStatus == ConnectionStatus.Disconnected) {
            colors.red
          } else {
            colors.secondaryLabel
          },
        maxLines = 1,
      )
    }

    Menu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = {
          Column {
            Text(
              text =
                if (store.isDemoMode) "demo" else "${store.parsedHost}:${store.parsedPort}",
              style = AppType.subheadline,
              color = colors.secondaryLabel,
            )
            Text(
              text = fullStatusText(store.connectionStatus, store.serverHost),
              style = AppType.subheadline,
              color = colors.secondaryLabel,
            )
          }
        },
        onClick = {},
        enabled = false,
      )

      HorizontalDivider(color = colors.separator)

      if (store.isConnectionRequested || store.connectionStatus.isConnected) {
        MenuItem(
          title = stringResource(R.string.disconnect),
          icon = R.drawable.ic_public_off,
          isDestructive = true,
        ) {
          expanded = false
          Haptics.impact(Haptics.ImpactStyle.Light)
          store.disconnect()
        }
      } else {
        MenuItem(title = stringResource(R.string.connect), icon = R.drawable.ic_language) {
          expanded = false
          Haptics.impact(Haptics.ImpactStyle.Light)
          store.connect()
        }
      }
    }
  }
}

/** A menu with rounded corners. */
@Composable
fun Menu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  offset: DpOffset = DpOffset.Zero,
  content: @Composable ColumnScope.() -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    offset = offset,
    shape = RoundedCornerShape(14.dp),
    containerColor = AppTheme.colors.menuBackground,
    content = content,
  )
}

/** A menu item with its icon at the trailing edge. */
@Composable
fun MenuItem(
  title: String,
  @androidx.annotation.DrawableRes icon: Int?,
  isDestructive: Boolean = false,
  onClick: () -> Unit,
) {
  val color = if (isDestructive) AppTheme.colors.red else AppTheme.colors.label
  DropdownMenuItem(
    text = { Text(text = title, style = AppType.body, color = color) },
    trailingIcon =
      if (icon != null) {
        {
          Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
          )
        }
      } else {
        null
      },
    onClick = onClick,
  )
}
