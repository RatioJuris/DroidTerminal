# Phase 3 UX review

Scope: walk each implemented flow, record only observed issues, then fix them without adding features.

## Environment

- Device: `192.168.31.177:5555` (Motorola One Power).
- Build: CI debug APK `builds/debug/droidterminal-debug-12-6b06d75563c69cf0440ac0543ecc870aaa45a6eb.apk` (run 12 / commit `6b06d75`).
- Method: ADB launch, taps/text via `input`, UI hierarchy via `uiautomator dump`, screenshots where useful.
- SSH test target `192.168.31.177:22` did not present an SSH host key: `Host key read failed: Host 192.168.31.177 did not present a key`. Connected terminal/SFTP flows could not be exercised against that endpoint.

## Findings

### High

1. **Connect failure is hidden behind the credential dialog.** In Terminal, selecting the `Lab` profile, entering a password and tapping `Connect` left the `Authenticate Lab` dialog open for the whole host-key fetch and failure period, with no progress or error inside it. The error only became visible after manually tapping `Cancel`. Expected: the dialog closes or displays the failure itself, and the user is not left guessing whether the tap registered. Source: ADB observation after Connect; hierarchy showed unchanged `Authenticate Lab` dialog at 3s, a further 10s, and after another Connect tap; underlying error appeared only after Cancel. Status: fixed and verified — after Connect the dialog closes and `Host key read failed: …` is visible in the terminal error state without tapping Cancel.

2. **Host-key fetch has no visible progress.** JSch timeout is 15s. During `fetchHostKey`, the credential dialog remains static with no spinner/status, so a slow or unreachable host looks frozen. Source: code `SshGateway.fetchHostKey` timeout plus observation in finding 1. Status: fixed and verified — with the profile host temporarily set to `192.0.2.1`, the hierarchy 1s after Connect showed `ProgressBar` at `[480,974][600,1094]` and `Connecting…` at `[414,1130][666,1190]`; the error appeared after the attempt completed.

### Medium

3. **Vault passphrase validation appears twice.** Entering a too-short passphrase and tapping Unlock showed `Passphrase must be at least 8 characters` both under the field and in the page-level error area. Source: hierarchy after `abc` + Unlock showed duplicate messages at bounds `[132,746][841,794]` and `[120,854][960,914]`. Status: fixed — page-level duplicate removed; field-level error retained. Pending device verification on rebuilt APK.

4. **Vault credential add result needs recheck.** After entering label `API` and value `secret`, the form remained until Save was tapped again; the list still showed `No credentials` immediately after. The flow was interrupted by an incoming call before a stable final state was captured. Source: ADB hierarchy observations; call screen appeared later. Status: unverified — re-walk after new build.

### Low

5. **Settings flow not yet walked.** No issue claimed; pending walkthrough.

6. **Shortcut keypad is squeezed when the terminal area is short.** Screenshot requested by user and captured to `C:\Users\Uplift\AppData\Local\Temp\opencode\droidterminal-keypad.png`. Hierarchy confirms row heights collapse instead of scrolling: first two key rows are 144px tall, rows three and four 84px, and the F-key row only 6–12px, with keys clipped before the bottom navigation. Status: fixed — keypad rows now live in a bounded `verticalScroll` area so each key keeps its 44.dp height and later rows scroll into view. Pending device verification on rebuilt APK.

## Fix plan

1. Close the credential dialog when connect/host-key fetch fails and surface the error in the terminal error state (with Retry). Show progress while fetching.
2. Remove duplicate passphrase validation presentation.
3. Make the shortcut keypad scrollable so keys keep full height instead of being squeezed.
4. Re-walk Vault add and Settings; update this file with results.
