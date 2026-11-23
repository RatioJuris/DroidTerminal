# Development roadmap

Status is based on merged code and passing GitHub Actions checks, not on design documents.

| Phase | Scope | Status |
| --- | --- | --- |
| 1 — Foundation | Repository audit, architecture, Android project, GPL licensing, CI, application shell, domain primitives and tests | **Complete — verified by GitHub Actions** |
| 2 — Terminal Engine | Batch 1 buffer/state, batch 2 parser/input, batch 3 verification, batch 4 Compose/device | **Batch 1 implemented — pushed for CI verification** |
| 3 — Shortcut Keypad | Compose keypad UI, modifier handling, long-press, custom layouts and persistence | Planned — domain model complete |
| 4 — SSH Engine | SSH-2, password/key auth, passphrases, host-key verification, profiles, interactive shell | Planned |
| 5 — Secure Vault | Keystore AES-256-GCM, vault unlock, biometrics, key import/generation/export | Planned |
| 6 — SFTP | Browse, upload/download, permissions, destructive-action confirmation, resume | Planned |
| 7 — Advanced Features | Multiple sessions, theming, settings, performance, Android lifecycle handling | Planned |
| 8 — Quality Assurance | Security review, dependency audit, UI/integration tests, signed APK/AAB | Planned |

## Phase 2 batches

1. **Buffer and state** — character model, screen buffer, cursor/screen state, bounded scrollback. Implemented and pushed for CI verification.
2. **Parser and input** — ANSI/VT escape sequences, control characters, terminal input processing, shortcut-keypad contracts and a testable local I/O adapter. Implemented and pushed for CI verification. No SSH.
3. **Engine verification** — targeted suite for parser sequences, buffer mutations and modifier/key-byte mappings.
4. **Compose and device validation** — render real terminal state in Compose, check keyboard/rotation/performance on a physical device, and keep GitHub Actions green.

Gradle dependency verification metadata will be generated once the dependency set stabilises.

## Definition of done for a phase

- Functionality is implemented, not mocked.
- Unit tests are added and pass in GitHub Actions.
- Relevant documentation is updated.
- Security-sensitive behaviour is explicitly tested.
- The phase is only then marked complete here.
