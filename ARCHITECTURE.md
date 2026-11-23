# Architecture

## Product shape

Droid Terminal is an Android application built with Kotlin, Jetpack Compose, AndroidX and Material 3. It is split into independent Gradle modules so terminal rendering, SSH transport, secure storage and UI can evolve without coupling.

## Module boundaries

```
app
 ├── core                 shared product metadata
 ├── terminal-engine      screen buffer, character model and VT byte handling
 ├── keypad               key model, byte sequences, modifier state, layouts
 ├── ssh-engine           SSH-2 transport, auth, host-key policy
 ├── vault                Android Keystore + AES-256-GCM credential storage
 ├── connections          profiles, groups and persistence
 ├── sftp                 remote file operations and transfers
 ├── sessions             multiple independent session lifecycle
 ├── settings             terminal/keypad/security preferences
 ├── security             crypto helpers and fingerprint utilities
 └── test-support         shared test fixtures
```

### Dependency rules

- `terminal-engine` is pure Kotlin: it does not depend on `ssh-engine`, Android UI or Compose. Bytes flow into it through `TerminalEmulator`, and immutable `TerminalSnapshot` values flow out to future renderers.
- `ssh-engine` does not depend on Compose or `app`; it exposes Kotlin flows for connection state and channel I/O.
- `vault` and `security` own all secret-handling code. Other modules receive opaque identifiers, never raw key material.
- `app` is the only module that assembles navigation and screens.
- `test-support` is test-only and must never be a runtime dependency of `app`.

## Data flow (target design)

```
Android keyboard / shortcut keypad
              │ terminal bytes
              ▼
      terminal-engine ──── screen model ────▶ Compose terminal view
              ▲
              │ channel I/O
      ssh-engine / session
              ▲
       vault (key lookup)     connections (profile)
```

Host-key verification is a separate policy step before an interactive shell is opened. A changed fingerprint blocks the connection until the user explicitly resolves it.

## Foundation decisions (Phase 1)

| Decision | Value | Reason |
| --- | --- | --- |
| Language | Kotlin 2.4.21 | Modern, stable Android language toolchain. |
| Build | AGP 9.5.2, Gradle 9.9.2 wrapper | Current stable AGP with compatible Gradle and JDK 17 baseline. |
| SDK | compile 37.2, target 36, min 26 | Compose 1.13.0 AAR metadata requires compileSdk 37.1+; min API avoids desugaring for `java.util.Base64`. |
| UI | Jetpack Compose BOM 2026.11.01, Material 3 | Declarative, testable mobile UI. |
| Navigation | State-driven destinations in `app` | Avoids an extra dependency until real screens exist. |
| Backups | Disabled | Protects future vault data from being copied out of the app sandbox. |
| Testing | JUnit unit tests in feature modules | Fast feedback in CI; instrumentation added with UI work. |

## Keypad model

The keypad module already defines the domain layer:

- `TerminalKey` — built-in and custom keys.
- `ModifierState` — momentary and locked Ctrl/Alt/Shift/Fn state.
- `KeySequences` — terminal byte generation, including `Ctrl+C` as ETX (`0x03`).
- `DefaultKeypadLayout` — the specified five-row layout.

UI, persistence of custom layouts and long-press behaviour are later phases.

## Terminal engine foundation

Phase 2 batch 1 adds a Unicode-aware cell model, fixed visible screen buffer, bounded scrollback with resize normalisation, deferred wrapping, C0 controls and immutable snapshots. Batch 2 adds an incremental VT parser emitting typed actions, CSI cursor/erase/reset/scroll-region operations, full SGR including 256-colour and truecolor, OSC title handling, SS3/charset/string-state handling, and keypad byte verification through the local `feed(ByteArray)` adapter. All state access is synchronised. Text selection and the Compose terminal view are later batches.

## Status

The Phase 1 foundation, keypad/security primitives and Phase 2 buffer/scrollback plus parser/input batches exist. The Compose terminal view, SSH, vault encryption, SFTP and session management are planned, not implemented.
