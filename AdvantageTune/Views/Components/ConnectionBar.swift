// Copyright (c) 2026 Littleton Robotics
// http://github.com/Mechanical-Advantage
//
// Use of this source code is governed by a BSD
// license that can be found in the LICENSE file
// at the root directory of this project.

import SwiftUI

public struct ConnectionBar: View {
  @Bindable var store: NTStore
  @FocusState private var isHostFocused: Bool

  public var body: some View {
    VStack(spacing: 6) {
      HStack(spacing: 8) {
        // Host input field
        HStack(spacing: 6) {
          Image(systemName: "network")
            .foregroundColor(.secondary)
            .font(.subheadline)

          TextField("Server IP or Hostname", text: $store.serverHost)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
            .keyboardType(.asciiCapable)
            .submitLabel(.done)
            .focused($isHostFocused)
            .onSubmit {
              store.reconnect()
            }

          if !store.serverHost.isEmpty {
            Button {
              store.serverHost = ""
            } label: {
              Image(systemName: "xmark.circle.fill")
                .foregroundColor(.secondary)
                .font(.caption)
            }
          }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

        // Connect / disconnect button
        Button {
          isHostFocused = false
          if store.isConnectionRequested || store.connectionStatus.isConnected {
            store.disconnect()
          } else {
            store.connect()
          }
        } label: {
          let isActive = store.isConnectionRequested || store.connectionStatus.isConnected
          Text(isActive ? "Disconnect" : "Connect" as LocalizedStringKey)
            .font(.subheadline.weight(.semibold))
            .foregroundColor(isActive ? .red : .accentColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(
              (isActive ? Color.red : Color.accentColor)
                .opacity(0.12)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .contentTransition(.identity)
        }
        .buttonStyle(.plain)
        .transaction { transaction in
          transaction.animation = nil
        }
      }

      // Status bar
      HStack {
        HStack(spacing: 6) {
          Circle()
            .fill(statusColor)
            .frame(width: 8, height: 8)

          statusTextView
            .font(.caption.weight(.medium))
            .foregroundColor(.secondary)
            .contentTransition(.identity)
        }
        .transaction { transaction in
          transaction.animation = nil
        }

        Spacer()

        Text("Port: \(String(store.parsedPort))")
          .font(.caption2)
          .foregroundColor(.secondary.opacity(0.8))
      }
      .padding(.horizontal, 4)
    }
    .padding(.horizontal)
    .padding(.top, 4)
    .padding(.bottom, 6)
    .background(Color(.systemBackground))
  }

  private var statusColor: Color {
    Self.statusColor(for: store.connectionStatus)
  }

  @ViewBuilder
  private var statusTextView: some View {
    Self.fullStatusTextView(for: store.connectionStatus, serverHost: store.serverHost)
  }

  fileprivate static func statusColor(for status: ConnectionStatus) -> Color {
    switch status {
    case .connected:
      return .green
    case .connecting:
      return .yellow
    case .disconnected:
      return .red
    }
  }

  @ViewBuilder
  fileprivate static func fullStatusTextView(
    for status: ConnectionStatus,
    serverHost: String
  ) -> some View {
    switch status {
    case .connected(let latencyMs):
      if latencyMs > 0 {
        let formattedLatency = latencyMs.formatted(.number.precision(.fractionLength(1)))
        Text("Connected (\(formattedLatency) ms)")
      } else {
        Text("Connected")
      }
    case .connecting:
      Text("Connecting to \(serverHost)...")
    case .disconnected:
      Text("Disconnected")
    }
  }
}

public struct ConnectionStatusPill: View {
  @Bindable var store: NTStore

  public init(store: NTStore) {
    self.store = store
  }

  public var body: some View {
    Menu {
      Section {
        Text(
          verbatim: store.isDemoMode
            ? "demo"
            : "\(store.parsedHost):\(String(store.parsedPort))"
        )
        ConnectionBar.fullStatusTextView(
          for: store.connectionStatus,
          serverHost: store.serverHost
        )
      }

      if store.isConnectionRequested || store.connectionStatus.isConnected {
        Button(role: .destructive) {
          Haptics.impact(.light)
          store.disconnect()
        } label: {
          Label("Disconnect", systemImage: "network.slash")
        }
      } else {
        Button {
          Haptics.impact(.light)
          store.connect()
        } label: {
          Label("Connect", systemImage: "network")
        }
      }
    } label: {
      HStack(spacing: 6) {
        Circle()
          .fill(ConnectionBar.statusColor(for: store.connectionStatus))
          .frame(width: 8, height: 8)

        compactStatusTextView
          .font(.caption.weight(.semibold))
          .foregroundColor(
            store.connectionStatus == .disconnected ? .red : .secondary
          )
          .lineLimit(1)
          .contentTransition(.identity)
      }
      .padding(.horizontal, 6)
      .transaction { transaction in
        transaction.animation = nil
      }
    }
  }

  @ViewBuilder
  private var compactStatusTextView: some View {
    switch store.connectionStatus {
    case .connected:
      Text("Connected")
    case .connecting:
      Text("Connecting...")
    case .disconnected:
      Text("Disconnected")
    }
  }
}
