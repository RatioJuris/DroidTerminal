# Development roadmap

Status is based on merged code and passing GitHub Actions checks, not on design documents.

| Phase | Scope | Status |
| --- | --- | --- |
| 1 — Foundation | Repository audit, architecture, Android project, GPL licensing, CI, application shell, domain primitives and tests | **In progress — implementation pushed for CI verification** |
| 2 — Terminal Engine | VT100/VT220/xterm emulator, ANSI colour, scrollback, resize, selection/copy/paste | Planned |
| 3 — Shortcut Keypad | Compose keypad UI, modifier handling, long-press, custom layouts and persistence | Planned — domain model complete |
| 4 — SSH Engine | SSH-2, password/key auth, passphrases, host-key verification, profiles, interactive shell | Planned |
| 5 — Secure Vault | Keystore AES-256-GCM, vault unlock, biometrics, key import/generation/export | Planned |
| 6 — SFTP | Browse, upload/download, permissions, destructive-action confirmation, resume | Planned |
| 7 — Advanced Features | Multiple sessions, theming, settings, performance, Android lifecycle handling | Planned |
| 8 — Quality Assurance | Security review, dependency audit, UI/integration tests, signed APK/AAB | Planned |

## Next actions after Phase 1 CI

1. Fix any compilation, lint or test failure reported by GitHub Actions.
2. Generate and commit Gradle dependency verification metadata.
3. Begin Phase 2 with a pure-Kotlin VT parser and snapshot model, keeping it independent of Android UI and SSH.
4. Add the Compose terminal view on top of the emulator snapshot model.
5. Proceed to the keypad UI, then SSH and vault work.

## Definition of done for a phase

- Functionality is implemented, not mocked.
- Unit tests are added and pass in GitHub Actions.
- Relevant documentation is updated.
- Security-sensitive behaviour is explicitly tested.
- The phase is only then marked complete here.
