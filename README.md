# Droid Terminal

**Droid Terminal** is a mobile-first Android SSH client and terminal emulator under development by PrajnaBytes International Private Limited. It is designed for administrators, developers, DevOps engineers and network engineers who need practical terminal control on a phone or tablet.

**Licence:** GPL-3.0-or-later. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).

**Privacy:** [The RatioJuris Privacy Promise](PRIVACY.md) — no analytics, no advertising, no developer-operated data collection and no mandatory RatioJuris cloud services.

## Branding

The Droid Terminal icon is the original artwork supplied by the project owner. The unchanged master SVG is stored at [`artwork/droid-terminal-icon.svg`](artwork/droid-terminal-icon.svg). Android launcher icons, including adaptive foreground and background layers, are generated from that master artwork.

## Current status

> **Phase 2 — Buffer and state.** The repository contains the verified Phase 1 foundation plus a pure-Kotlin terminal screen buffer with bounded scrollback, Unicode-aware character handling, C0 control processing and incremental UTF-8 decoding. ANSI escape sequences, the Compose terminal view, SSH, the secure vault, SFTP and session management are **not implemented yet**. The application shell says so explicitly rather than presenting mock functionality as finished.

Repository audit at the start of Phase 1: the GitHub repository contained only a one-line `README.md`, with no source, build files, licence or CI. There was no existing implementation to preserve.

## Why Droid Terminal

Android keyboards do not provide convenient access to the keys that terminal work depends on: `Ctrl+C`, `Ctrl+D`, `Ctrl+Z`, `Ctrl+A`, `Ctrl+E`, `Ctrl+L`, `Ctrl+R`, `Ctrl+W`, `Ctrl+U`, `Ctrl+K`, `Esc`, `Tab`, `Alt`, arrows, function keys, `Home`, `End`, `Page Up` and `Page Down`.

Droid Terminal will solve this with a compact, configurable shortcut keypad that works alongside the Android keyboard, backed by an encrypted vault for SSH keys and credentials and strict SSH host-key verification.

## Planned capabilities

- VT100/VT220/xterm-compatible terminal rendering with ANSI colour, cursor control, scrollback, text selection and clipboard integration.
- Five-row configurable shortcut keypad with modifier state, custom keys and custom layouts.
- SSH-2 with password and public-key authentication, encrypted private keys, keepalive and explicit host-key trust decisions.
- Android Keystore-backed AES-256-GCM vault with password/PIN, biometric/device-credential gates and inactivity locking.
- SFTP browser with uploads, downloads and managed destructive operations.
- Multiple independent sessions with per-session terminal and keypad state.
- Signed APK and AAB releases produced by GitHub Actions.

## Modules

| Module | Purpose |
| --- | --- |
| `app` | Android application shell and Material 3 navigation |
| `core` | Shared product metadata |
| `terminal-engine` | Screen buffer, character model and VT byte handling |
| `keypad` | Shortcut key model, sequences, modifier state and layouts |
| `ssh-engine` | SSH transport and authentication |
| `vault` | Encrypted credential and key storage |
| `connections` | Connection profiles and groups |
| `sftp` | SFTP file operations |
| `sessions` | Session lifecycle and switching |
| `settings` | Preferences |
| `security` | Cryptographic helpers and host-key fingerprint utilities |
| `test-support` | Shared automated-test utilities |

## Build

Authoritative builds run in GitHub Actions; see [BUILD.md](BUILD.md). A local Android SDK is optional and is not part of the release process.

```powershell
.\gradlew --no-daemon testDebugUnitTest lint assembleDebug
```

## Documentation

- [PRIVACY.md](PRIVACY.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [SECURITY.md](SECURITY.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [BUILD.md](BUILD.md)
- [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md)
- [CHANGELOG.md](CHANGELOG.md)
- [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES)
