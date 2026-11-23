# Changelog

All notable changes to Droid Terminal are recorded here. The format follows Keep a Changelog-style categories.

## [Unreleased]

### Added

- Phase 2 batch 1 terminal foundation: Unicode-aware cell model, visible screen buffer, bounded scrollback, deferred wrapping, C0 control handling, incremental UTF-8 decoding and immutable snapshots.
- Targeted terminal-engine tests for character widths, buffer mutations, scrolling, resizing, control bytes, malformed UTF-8 and unsupported escape sequences.

## [0.1.0] — 2026-11-23

### Added

- GPL-3.0-or-later licence, NOTICE and third-party notices.
- Gradle 9.9.2 wrapper, AGP 9.5.2, Kotlin 2.4.21 and version catalog.
- Twelve-module Android project structure.
- Material 3 application shell with explicit implementation-status screens.
- Keypad domain model: keys, modifier state, default layout and byte sequences.
- Terminal size model and host-key fingerprint utility.
- Unit tests for all Phase 1 domain objects.
- GitHub Actions CI and release workflows with manual dispatch, version validation, signature verification and non-destructive rebuilds.
- Original Droid Terminal icon artwork and generated Android launcher resources.
- The RatioJuris Privacy Promise.
- Architecture, security, privacy, contributing, build and roadmap documentation.

### Changed

- Repository initialised from a single-line README into a full Android project skeleton.

### Not implemented

- Terminal emulation, SSH, vault encryption, SFTP, session management and custom keypad UI.
