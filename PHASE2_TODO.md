# Phase 2 TODO

Every placeholder introduced in Phase 1. Each entry maps to a `// TODO(phase2):`
comment at the stated location. Phase 2 is complete when this list is empty and
no `TODO(phase2)` markers remain.

| # | File | Item |
| --- | --- | --- |
| 1 | `app/.../ui/terminal/TerminalScreen.kt` | Replace the timed connection transition with real SSH connection state. |
| 2 | `app/.../ui/terminal/TerminalScreen.kt` | Follow the live terminal engine cursor instead of line count for auto-scroll. |
| 3 | `app/.../ui/terminal/TerminalScreen.kt` | Open and close a real SSH session when Connect/Disconnect is tapped. |
| 4 | `app/.../ui/terminal/TerminalScreen.kt` | Encode input with `KeySequences` and write bytes to the session channel. |
| 5 | `app/.../ui/terminal/TerminalScreen.kt` | Replace mock output lines with `TerminalSnapshot` rendering and scrollback/selection. |
| 6 | `app/.../ui/terminal/TerminalScreen.kt` | Generate real keypad byte sequences for shortcut keys. |
| 7 | `app/.../ui/connections/ConnectionsScreen.kt` | Replace timed refresh with a real profile repository. |
| 8 | `app/.../ui/connections/ConnectionsScreen.kt` | Persist created and edited profiles through the connections module. |
| 9 | `app/.../ui/connections/ConnectionsScreen.kt` | Delete persisted profiles and revoke their stored secrets. |
| 10 | `app/.../ui/connections/ConnectionsScreen.kt` | Obtain live host-key fingerprints and enforce verification policy. |
| 11 | `app/.../ui/vault/VaultScreen.kt` | Unlock the Android Keystore-backed vault instead of accepting any long passphrase. |
| 12 | `app/.../ui/vault/VaultScreen.kt` | Wipe in-memory plaintext when the vault is locked. |
| 13 | `app/.../ui/vault/VaultScreen.kt` | Wire the biometric prompt and keystore-gated access. |
| 14 | `app/.../ui/vault/VaultScreen.kt` | Bind auto-lock to an app-background timer. |
| 15 | `app/.../ui/vault/VaultScreen.kt` | Encrypt and persist added credentials through the vault module. |
| 16 | `app/.../ui/vault/VaultScreen.kt` | Require explicit authenticated action before revealing a secret. |
| 17 | `app/.../ui/vault/VaultScreen.kt` | Delete ciphertext and purge the associated Keystore key. |
| 18 | `app/.../ui/sftp/SftpScreen.kt` | Refresh directories through a real SFTP session. |
| 19 | `app/.../ui/sftp/SftpScreen.kt` | List directories from the remote filesystem instead of a fixed map. |
| 20 | `app/.../ui/sftp/SftpScreen.kt` | Enqueue real SFTP downloads with progress reporting. |
| 21 | `app/.../ui/sftp/SftpScreen.kt` | Issue real SFTP rename operations and refresh the directory. |
| 22 | `app/.../ui/sftp/SftpScreen.kt` | Perform confirmed SFTP deletes with audit logging. |
| 23 | `app/.../ui/settings/SettingsScreen.kt` | Persist terminal appearance preferences. |
| 24 | `app/.../ui/settings/SettingsScreen.kt` | Write all preferences to DataStore and bind them to consumers. |
| 25 | `app/.../ui/model/UiModels.kt` | Replace `MockData` with repositories from connections, vault, sftp and sessions. |

## Cross-cutting Phase 2 work not represented by a UI marker

- Wire `ssh-engine`, `vault`, `connections`, `sftp` and `sessions` modules into `app`.
- Add error handling that surfaces real transport, auth and storage failures.
- Add UI/instrumentation tests for navigation and form flows.
