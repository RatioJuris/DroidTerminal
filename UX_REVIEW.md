# Phase 3 UX review

Scope: walk each implemented flow, record only observed issues, then fix them without adding features.

## Environment

- Device: `192.168.31.177:5555` (Motorola One Power).
- Builds: `builds/debug/droidterminal-debug-14-1dfbf2338a628558a4cb1fc71d1d3c28e7b179ee.apk` (run 14 / commit `1dfbf23`) for keypad/validation/Settings checks; `builds/debug/droidterminal-debug-15-85c74984757e464de0f1e16eed064c886a4a1f3e.apk` (run 15 / commit `85c7498`) for the vault crypto fix.
- Method: ADB launch, taps/text via `input`, UI hierarchy via `uiautomator dump`, screenshots where useful.
- SSH test target `192.168.31.177:22` did not present an SSH host key: `Host key read failed: Host 192.168.31.177 did not present a key`. Connected terminal/SFTP flows could not be exercised against that endpoint.

## Findings

### High

1. **Connect failure is hidden behind the credential dialog.** In Terminal, selecting the `Lab` profile, entering a password and tapping `Connect` left the `Authenticate Lab` dialog open for the whole host-key fetch and failure period, with no progress or error inside it. The error only became visible after manually tapping `Cancel`. Expected: the dialog closes or displays the failure itself, and the user is not left guessing whether the tap registered. Source: ADB observation after Connect; hierarchy showed unchanged `Authenticate Lab` dialog at 3s, a further 10s, and after another Connect tap; underlying error appeared only after Cancel. Status: fixed and verified — after Connect the dialog closes and `Host key read failed: …` is visible in the terminal error state without tapping Cancel.

2. **Host-key fetch has no visible progress.** JSch timeout is 15s. During `fetchHostKey`, the credential dialog remains static with no spinner/status, so a slow or unreachable host looks frozen. Source: code `SshGateway.fetchHostKey` timeout plus observation in finding 1. Status: fixed and verified — with the profile host temporarily set to `192.0.2.1`, the hierarchy 1s after Connect showed `ProgressBar` at `[480,974][600,1094]` and `Connecting…` at `[414,1130][666,1190]`; the error appeared after the attempt completed.

### Medium

3. **Vault passphrase validation appears twice.** Entering a too-short passphrase and tapping Unlock showed `Passphrase must be at least 8 characters` both under the field and in the page-level error area. Source: hierarchy after `abc` + Unlock showed duplicate messages at bounds `[132,746][841,794]` and `[120,854][960,914]`. Status: fixed and verified — on build 14, hierarchy after `abc` + Unlock showed a single message at `[132,746][841,794]`.

4. **Vault credential save fails with a crypto error.** On build 14, unlocking the vault, adding label `API` / value `secret` and tapping Save produced `Save failed: Caller-provided IV not permitted` with a Retry action; the credential was never stored. Root cause: `VaultCrypto.encrypt` passed a `GCMParameterSpec` into `cipher.init(ENCRYPT_MODE, …)` for per-item Android Keystore keys, which Android Keystore forbids. Status: fixed and verified on build 15 — Save now closes the form and the list shows `1 stored item(s)` / `API` / `Password · updated 2026-11-24`; `View` → `Reveal` decrypts and displays `secret`. Encryption lets the cipher generate the IV and reads it back via `cipher.iv`; decrypt still supplies the stored IV.

### Low

5. **Settings flow walked — no issue found.** On build 14 the Settings page is a `ScrollView` (`scroll=true`) and all sections are reachable: Terminal (font size, scrollback, keypad toggles), Security (auto-lock, biometric, host-key policy), Actions (Save, Reset), About. Toggling `Auto-lock vault when backgrounded` and tapping Save produced `Preferences saved` at `[36,1502][352,1550]` with the About section reflowed below it, no overlap or clipping. No fix required.

6. **Shortcut keypad is squeezed when the terminal area is short.** Screenshot requested by user and captured to `C:\Users\Uplift\AppData\Local\Temp\opencode\droidterminal-keypad.png`. Hierarchy confirms row heights collapse instead of scrolling: first two key rows are 144px tall, rows three and four 84px, and the F-key row only 6–12px, with keys clipped before the bottom navigation. First fix (build 14) made it a bounded `ScrollView`: keys kept full height but only three rows were visible and F1–F6 required swiping, which the user reported as worse. Status: fixed and verified on build 18 — removed the redundant `Shortcut keys` header, reduced keys to 34dp, and set a 216dp viewport. The parent grants 564px, which now holds all five rows: Ctrl row `[108,1328][122,1382]`, arrow row `[108,1436][122,1490]`, Ctrl+C row `[108,1544][122,1598]`, Ctrl+E row `[108,1652][122,1706]`, F1–F6 `[108,1760][122,1814]` — all inside the viewport ending at 1862. No scrolling needed at default settings; `verticalScroll` remains only as a safety net for smaller screens or larger fonts. Screenshot: `C:\Users\Uplift\AppData\Local\Temp\opencode\droidterminal-keypad-allrows.png`.

7. **Connections flow walked — no issue found.** On build 15 the empty state offers `Add profile`; the `New connection` form exposes Profile name, Host, Port (default `22`), Username, Group and Authentication, all reachable with the keyboard hidden. Saving `Lab` / `192.0.2.1` / `tester` produced `1 profile(s)` with `tester@192.0.2.1:22`, `Password`, `General` and Edit / Host key / Delete actions. No fix required.

## Fix plan

1. Close the credential dialog when connect/host-key fetch fails and surface the error in the terminal error state (with Retry). Show progress while fetching.
2. Remove duplicate passphrase validation presentation.
3. Make the shortcut keypad scrollable so keys keep full height instead of being squeezed.
4. Fix Keystore GCM encryption so vault credential save succeeds.
5. Re-walk Settings and Connections; update this file with results.

All fix-plan items are complete. Remaining unverified path: connected terminal/SFTP happy path, blocked because `192.168.31.177:22` does not present an SSH host key.

## Phase 3 follow-up — workspace changes

User-requested changes after the first walkthrough: remove the keyboard on/off switch row, make bottom navigation icon-only, use clearer icons, remove the empty-session text, and merge Connections into Vault.

### High

8. **QWERTY keyboard rows were clipped in portrait.** On `1080x2246` at density `480`, the bottom letter row and control row reported `[0,0][0,0]` bounds and were unreachable; hiding the F-row restored the letters but not the control row. Source: `InAppKeyboard.kt` docked a 40dp header plus six 48dp rows with 4dp spacing into about 260dp. Status: fix implemented, pending device verification — removed the keyboard switch row, navigation labels and empty-session text; reduced key/button height to 42dp and header buttons to 36dp; and placed the key grid in a bounded 260dp area that scrolls both vertically and horizontally with fixed-width keys.

### Medium

9. **Bottom navigation labels consumed vertical space.** The five labelled items made the bar taller without adding information because the selected destination is already shown in the top bar. Status: fix implemented, pending device verification — `NavigationBarItem` now renders icon-only; `contentDescription = destination.label` keeps screen-reader access. Terminal uses the terminal icon, Vault uses a key icon, SFTP uses the network icon, and Settings keeps the gear. No `Vault` vector exists in `material-icons-extended` 1.7.8, verified from the cached AAR, so the key icon is the closest available vault affordance.

10. **Connections and Vault were separate destinations even though the vault is the credential store.** Status: fix implemented, pending device verification — Connections is removed from bottom navigation; `ConnectionProfilesSection` is embedded in the unlocked Vault screen with the credential and key items. Connection profiles remain in the existing `ConnectionRepository`/DataStore; no encrypted-storage migration was attempted in Phase 3.

11. **Empty terminal showed redundant status text.** `No terminal sessions` occupied space above the connect banner. Status: fixed in code, pending device verification — `SessionTabs` returns immediately when there are no tabs; the actionable `No session selected` banner remains.

Keyboard visibility after removing the switch row remains governed by the saved "Show keyboard by default" setting when Terminal is entered; the keyboard's `Hide` button and Back still hide it for the current visit.
