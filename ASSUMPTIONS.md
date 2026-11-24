# Assumptions

Recorded when the three-phase build directive was received. These resolve conflicts
between the new directive and the standing repository constraints without blocking work.

- **Build outputs are versioned, not ephemeral.** CI publishes each debug APK to the tracked `builds/debug/` directory and commits it to `develop` instead of leaving it only as an expiring Actions artifact. Reports remain artifacts. The workflow uses `contents: write` and `GITHUB_TOKEN`; token-originated commits do not re-trigger CI. After any CI publish, a local push must first integrate the bot commit (`git pull --rebase origin develop`). APKs are append-only and will grow repository history; this is accepted for the requested retention model.

- **New directive governs planning.** The three-phase plan (complete UI → fill placeholders → UX review) supersedes the previously delivered Phase 2 Batch 3 engine-verification plan. Engine modules are not revisited or modified in these phases.
- **Marked placeholders are now authorised.** The earlier "no mock UI or demo data" constraint is read as prohibiting mock data *presented as finished*. The new directive explicitly required mock data plus Phase 2 markers and `PHASE2_TODO.md`, so all placeholder data was clearly marked and inventoried.
- **Build authority unchanged.** GitHub Actions remains the only build/test authority. "The app runs without errors" is verified by CI compile, Android lint and unit tests on `develop`; no local Gradle invocation. Physical/visual rendering is a device-validation activity and cannot be asserted locally under this policy.
- **Branch and commit rules unchanged.** `develop` only, never `main`; commit subjects at most 15 characters.
- **State lifetime.** Phase 1 screen state is in-memory via `remember`; complex model lists are not saved across process death. Persistence is a Phase 2 item and is listed in `PHASE2_TODO.md`.
- **Icon surface.** Only icon vectors already proven available in `material-icons-core` are used; other affordances use text labels to avoid adding a dependency during Phase 1.

## Phase 2

- **Empty modules are in scope.** `ssh-engine`, `vault`, `connections`, `sftp`, `sessions` and `settings` are empty Gradle shells with no source. Phase 2 implements their real APIs before replacing UI placeholders; this is filling the declared Phase 1 TODOs, not new scope.
- **Transport library.** SSH and SFTP use `com.github.mwiede:jsch:2.28.8`, the maintained JSch fork. Version verified from Maven Central `maven-metadata.xml`; the public API (`com.jcraft.jsch` package, `HostKeyRepository`, `ChannelSftp`) was verified against the 2.28.8 sources jar.
- **Persistence library.** Preferences, connection profiles and trusted host keys use `androidx.datastore:datastore-preferences:1.3.0` (verified from Google Maven metadata). Records are encoded as Base64 field lists so no additional serialisation dependency is required.
- **Vault design.** The vault uses Android Keystore AES-GCM keys. Passphrase enrolment stores a PBKDF2-with-HMAC-SHA256 verifier; unlock decrypts that verifier. Secrets are encrypted with per-item Keystore keys so deletion can purge the associated key. Plaintext exists only in the unlocked in-memory map and is cleared on lock or auto-lock. A separate user-authentication-gated Keystore key backs biometric reveal.
- **Biometric host.** `androidx.biometric:biometric:1.4.0-rc01` only accepts `FragmentActivity` or `Fragment`, so `MainActivity` changes from `ComponentActivity` to `FragmentActivity` (a direct subclass, so `setContent` and edge-to-edge are unchanged). `androidx.fragment:fragment-ktx:1.9.1` is added; all versions verified from Google Maven metadata.
- **Coroutines and state.** `kotlinx-coroutines-android:1.10.2` and `lifecycle-viewmodel-compose:2.11.1` are used for async transport work and observable state; versions verified from Maven Central and Google Maven metadata respectively.
- **Permissions.** `android.permission.INTERNET` is added to the manifest because real SSH/SFTP requires network access. No other permission is added.
- **Session ownership.** SSH sessions and terminal emulators are application-scoped in the sessions module, so navigation between destinations does not drop a live connection. Screen-level form state remains local.
- **Connection credentials.** The terminal uses a small per-connect credential dialog for a password or an inline PEM private key with optional key passphrase. Credentials are passed to the SSH engine for one attempt and are never written to DataStore or the vault by this flow.
- **Host-key policy mapping.** `Warn` reads the live fingerprint and requires explicit out-of-band acceptance; `Strict` refuses to connect until a fingerprint has been accepted; `Trust on first use` records the first presented fingerprint automatically and still rejects later changes.
- **Agent auth.** `AGENT` remains selectable in the Phase 1 form but is not implemented in Phase 2. Selecting it for a terminal connection surfaces a clear error rather than silently falling back to another method.
- **Keypad input.** Shortcut keys emit real terminal byte sequences from the keypad module. Momentary modifiers toggle on tap; when the saved long-press setting is enabled, a long press locks the modifier until it is tapped again or cleared.

## Phase 3

- **Debug APK signature churn.** Each GitHub-hosted CI run generates a fresh Android debug signing key, so a newly published APK cannot be installed over the previous one with `install -r`. For device verification the old debug app is uninstalled and its test-only data (the `Lab` profile and `API` vault item created during the walkthrough) is recreated. This is a test-device action only; no user data was present in the app before the walkthrough.

## Custom keyboard

- **Branch and CI.** Work is on `custom-keyboard` branched from `develop` at `7fa7ce4`; nothing is merged. The user chose "push branch to CI", which overrides the earlier "do not push" instruction. `.github/workflows/ci.yml` only fires on pushes to `develop` or PRs targeting `develop`/`main`, so each phase is pushed and a **draft PR to `develop`** is opened to trigger Actions. The PR is never merged. Local Gradle is still not invoked.
- **Layering.** The `keypad` module stays pure JVM (byte encoding only, no Compose dependency). The Compose keyboard UI is a new app-module component. Byte output continues to flow through `KeySequences` → `SessionManager.write`.
- **portrait key width conflict.** A literal 40dp minimum on the main letter rows is geometrically impossible on a ~360dp portrait phone: row 2 has 14 keys, requiring ~560dp. Resolution: keys fill the available width with no horizontal scrolling on letter rows, keep a 48dp hit height, and use a smaller width on narrow portrait screens. F-row is horizontally scrollable. Phase 3 reviews actual tappability.
- **Modifier model stays source-compatible.** `ModifierState` keeps `pressed`/`locked` so existing consumers compile; `consume()` clears one-shot modifiers after the next key. Tap = one-shot, double-tap = locked, tap-again = off. `longPressModifier` is removed as dead configuration.
- **Physical keyboard path.** Removing the command field would otherwise regress hardware-key input. The terminal surface is made focusable with `onKeyEvent` routing hardware keys through the same byte path. No IME is involved because a plain focusable surface has no `InputConnection`. Full xterm parity for hardware keys is Phase 2.
- **System keyboard fallback.** The setting is added and functional in Phase 1 (shows a plain text field + Send only when enabled). Deep IME/bracketed-paste behaviour is Phase 2.
- **Selection pad.** Phase 1 renders the pad and routes action callbacks; terminal-buffer selection, clipboard access and bracketed paste are Phase 2 TODOs. The `SELECT` badge tracks the active pad in Phase 1 and real selection mode in Phase 2.

### Phase 2 encoding and selection decisions

- **xterm modifier parameters.** Modified navigation/function keys use `CSI 1;modifier` (or `CSI number;modifier~`) with modifier = 1 + Shift + 2×Alt + 4×Ctrl + 8×Meta. Physical Meta is mapped to the 8 bit. DECCKM (DECSET 1) switches only unmodified arrows/Home/End to SS3; modified forms stay CSI. F5–F12 use xterm's non-contiguous CSI numbers 15, 17, 18, 19, 20, 21, 23, 24.
- **Byte encoding.** Printable Unicode keys are sent as UTF-8. `TerminalKey.Custom` keeps its documented single-byte Latin-1 encoding for user-defined sequences.
- **Selection model.** Selection rows are absolute indices across scrollback plus the visible screen. Word selection treats letters, digits and `_` as word characters. Touch long-press selects the row; tapping another row moves the selection head while preserving its column. Selection shifts by one row when a full scrollback evicts a line and clears when its anchor would leave the buffer. A column-width resize clears selection because absolute row meaning changes.
- **Clipboard.** Copy writes the trimmed selected text to the Android clipboard and exits selection mode. Paste strips ESC and NUL before transmission so pasted content cannot inject escape sequences; DECSET 2004 state is tracked by the emulator and reset by RIS, and only then wraps paste in `ESC[200~` / `ESC[201~`.
- **Fallback input boundary.** When "Use system keyboard instead" is enabled, the raw hardware `onKeyEvent` handler returns false so the text field and IME own hardware input, while the default in-app-keyboard path continues to route hardware keys directly to the pty.
- **Privacy surface.** Keyboard handling adds no keystroke logging, no network use and no system-IME registration. Byte output still flows only through `SessionManager.write`.
