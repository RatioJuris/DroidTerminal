# Phase 2 TODO

Phase 1 markers are `TODO(phase2)` in source. Each row maps to the file and the
work required. This file must be empty when Phase 2 is complete.

| # | File | Work |
|---|---|---|
| 1 | `keypad/src/main/kotlin/com/ratiojuris/droidterminal/keypad/KeySequences.kt` | Handle DECCKM (`ESC O` for arrows/Home/End) and full xterm modifier parameters for modified arrows, Home/End, PgUp/PgDn, Ins, Del and F-keys. |
| 2 | `keypad/src/main/kotlin/com/ratiojuris/droidterminal/keypad/KeySequences.kt` | Verify Ctrl/Alt/Shift precedence and printable-character encoding against xterm; decide Unicode handling beyond single-byte Latin-1. |
| 3 | `terminal-engine/src/main/kotlin/com/ratiojuris/droidterminal/terminalengine/TerminalEmulator.kt` | Track DECSET 2004 (bracketed paste) and DECCKM state from incoming escape sequences. |
| 4 | `terminal-engine/src/main/kotlin/com/ratiojuris/droidterminal/terminalengine/TerminalScreen.kt` | Add buffer selection state and Select / Select All / Select Word / Select Line / Clear operations that the pad can drive. |
| 5 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/InAppKeyboard.kt` | Bind selection-pad arrow and action callbacks to terminal selection mode, including the `SELECT` badge and button highlight. |
| 6 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/TerminalScreen.kt` | Implement Copy via Android clipboard (exit selection mode) and a brief hint when nothing is selected. |
| 7 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/TerminalScreen.kt` | Implement Paste via Android clipboard with `ESC [ 200~ … ESC [ 201~` when bracketed paste is enabled; never append a newline. |
| 8 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/TerminalScreen.kt` | Keep touch selection on the terminal in sync with the selection pad. |
| 9 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/TerminalScreen.kt` | Bring physical-keyboard routing to full xterm parity (Meta, modified F-keys, DECCKM) and verify against vim, nano, tmux, htop, less and bash readline. |
| 10 | `app/src/main/kotlin/com/ratiojuris/droidterminal/ui/terminal/TerminalScreen.kt` | Finish system-keyboard fallback behaviour, including IME focus handling without registering a system IME. |
| 11 | Whole keyboard path | Confirm no keystroke logging, no keyboard network use and no system-IME registration. |
