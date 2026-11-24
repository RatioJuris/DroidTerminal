/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.ratiojuris.droidterminal.keypad.ArrowDirection
import com.ratiojuris.droidterminal.keypad.Modifier as KeypadModifier
import com.ratiojuris.droidterminal.keypad.ModifierState
import com.ratiojuris.droidterminal.keypad.TerminalKey
import kotlinx.coroutines.delay

/** Which pad is currently docked at the bottom of the terminal. */
enum class KeyboardPad { KEYS, SELECT }

/** Actions exposed by the selection pad. Terminal behaviour is wired in Phase 2. */
enum class SelectionAction {
    SELECT,
    SELECT_ALL,
    SELECT_WORD,
    SELECT_LINE,
    COPY,
    PASTE,
    CLEAR,
    MOVE_LEFT,
    MOVE_UP,
    MOVE_DOWN,
    MOVE_RIGHT,
}

/** A tap or a double-tap on a sticky modifier key. */
enum class ModifierTap { SINGLE, DOUBLE }

private data class KeyDef(
    val label: String,
    val shiftedLabel: String = label,
    val key: TerminalKey,
    val shiftedKey: TerminalKey = key,
    val weight: Float = 1f,
    val repeatable: Boolean = false,
    val preview: Boolean = false,
    val modifier: KeypadModifier? = null,
    val caps: Boolean = false,
)

private fun key(
    label: String,
    key: TerminalKey,
    shiftedLabel: String = label,
    shiftedKey: TerminalKey = key,
    weight: Float = 1f,
    repeatable: Boolean = false,
    preview: Boolean = false,
    modifier: KeypadModifier? = null,
    caps: Boolean = false,
): KeyDef = KeyDef(label, shiftedLabel, key, shiftedKey, weight, repeatable, preview, modifier, caps)

private fun charKey(lower: Char, upper: Char, preview: Boolean = true): KeyDef = key(
    label = lower.toString(),
    key = TerminalKey.Character(lower),
    shiftedLabel = upper.toString(),
    shiftedKey = TerminalKey.Character(upper),
    preview = preview,
)

private val numberRow = listOf(
    charKey('`', '~', preview = false),
    charKey('1', '!', preview = false),
    charKey('2', '@', preview = false),
    charKey('3', '#', preview = false),
    charKey('4', '$', preview = false),
    charKey('5', '%', preview = false),
    charKey('6', '^', preview = false),
    charKey('7', '&', preview = false),
    charKey('8', '*', preview = false),
    charKey('9', '(', preview = false),
    charKey('0', ')', preview = false),
    charKey('-', '_', preview = false),
    charKey('=', '+', preview = false),
    key("⌫", TerminalKey.Backspace, weight = 1.6f, repeatable = true),
)

private val topLetterRow = listOf(
    key("Tab", TerminalKey.Tab, weight = 1.4f),
    charKey('q', 'Q'),
    charKey('w', 'W'),
    charKey('e', 'E'),
    charKey('r', 'R'),
    charKey('t', 'T'),
    charKey('y', 'Y'),
    charKey('u', 'U'),
    charKey('i', 'I'),
    charKey('o', 'O'),
    charKey('p', 'P'),
    charKey('[', '{', preview = false),
    charKey(']', '}', preview = false),
    key("\\", TerminalKey.Character('\\'), shiftedLabel = "|", shiftedKey = TerminalKey.Character('|'), weight = 1.2f),
)

private val homeRow = listOf(
    key("Caps", TerminalKey.CapsLock, weight = 1.4f, caps = true),
    charKey('a', 'A'),
    charKey('s', 'S'),
    charKey('d', 'D'),
    charKey('f', 'F'),
    charKey('g', 'G'),
    charKey('h', 'H'),
    charKey('j', 'J'),
    charKey('k', 'K'),
    charKey('l', 'L'),
    charKey(';', ':', preview = false),
    charKey('\'', '"', preview = false),
    key("Enter", TerminalKey.Enter, weight = 1.5f),
)

private val bottomLetterRow = listOf(
    key("Shift", TerminalKey.Shift, weight = 1.6f, modifier = KeypadModifier.SHIFT),
    charKey('z', 'Z'),
    charKey('x', 'X'),
    charKey('c', 'C'),
    charKey('v', 'V'),
    charKey('b', 'B'),
    charKey('n', 'N'),
    charKey('m', 'M'),
    charKey(',', '<', preview = false),
    charKey('.', '>', preview = false),
    charKey('/', '?', preview = false),
    key("Shift", TerminalKey.Shift, weight = 1.6f, modifier = KeypadModifier.SHIFT),
)

private val controlRow = listOf(
    key("Ctrl", TerminalKey.Control, modifier = KeypadModifier.CONTROL),
    key("Alt", TerminalKey.Alt, modifier = KeypadModifier.ALT),
    key("Space", TerminalKey.Character(' '), weight = 3.2f),
    key("←", TerminalKey.Arrow(ArrowDirection.LEFT), repeatable = true),
    key("↑", TerminalKey.Arrow(ArrowDirection.UP), repeatable = true),
    key("↓", TerminalKey.Arrow(ArrowDirection.DOWN), repeatable = true),
    key("→", TerminalKey.Arrow(ArrowDirection.RIGHT), repeatable = true),
    key("Home", TerminalKey.Home),
    key("End", TerminalKey.End),
    key("PgUp", TerminalKey.PageUp, repeatable = true),
    key("PgDn", TerminalKey.PageDown, repeatable = true),
    key("Ins", TerminalKey.Insert),
)

private val functionRow = listOf(
    key("Esc", TerminalKey.Escape),
    *(1..12).map { key("F$it", TerminalKey.FunctionKey(it)) }.toTypedArray(),
    key("Del", TerminalKey.Delete, repeatable = true),
)

/**
 * Reusable in-app keyboard docked below the terminal. All keys produce
 * [TerminalKey] values; byte encoding stays in the keypad module.
 */
@Composable
fun InAppKeyboard(
    modifiers: ModifierState,
    capsLock: Boolean,
    pad: KeyboardPad,
    selectionActive: Boolean,
    showFunctionRow: Boolean,
    haptics: Boolean,
    onKey: (TerminalKey) -> Unit,
    onModifierTap: (KeypadModifier, ModifierTap) -> Unit,
    onToggleCaps: () -> Unit,
    onTogglePad: () -> Unit,
    onToggleFunctionRow: () -> Unit,
    onClearModifiers: () -> Unit,
    onSelectionAction: (SelectionAction) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shiftActive = modifiers.isEnabled(KeypadModifier.SHIFT)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = pad == KeyboardPad.KEYS,
                onClick = onTogglePad,
                label = { Text("Keys", style = MaterialTheme.typography.labelMedium) },
            )
            FilterChip(
                selected = pad == KeyboardPad.SELECT,
                onClick = onTogglePad,
                label = { Text("Select", style = MaterialTheme.typography.labelMedium) },
            )
            if (pad == KeyboardPad.SELECT || selectionActive) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "SELECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            SpacerWeight()
            SmallButton("Fx", onToggleFunctionRow)
            SmallButton("Clr", onClearModifiers)
            SmallButton("Hide", onHide)
        }

        if (pad == KeyboardPad.KEYS) {
            if (showFunctionRow) {
                ScrollableKeyRow(functionRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps)
            }
            WeightedKeyRow(numberRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps, smallText = true)
            WeightedKeyRow(topLetterRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps, smallText = true)
            WeightedKeyRow(homeRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps, smallText = true)
            WeightedKeyRow(bottomLetterRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps, smallText = true)
            ScrollableKeyRow(controlRow, modifiers, shiftActive, capsLock, haptics, onKey, onModifierTap, onToggleCaps)
        } else {
            SelectPad(selectionActive, onSelectionAction)
        }
    }
}

@Composable
private fun RowScope.SpacerWeight() {
    Box(modifier = Modifier.weight(1f))
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WeightedKeyRow(
    keys: List<KeyDef>,
    modifierState: ModifierState,
    shiftActive: Boolean,
    capsLock: Boolean,
    haptics: Boolean,
    onKey: (TerminalKey) -> Unit,
    onModifierTap: (KeypadModifier, ModifierTap) -> Unit,
    onToggleCaps: () -> Unit,
    smallText: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { def ->
            KeyboardKey(
                def = def,
                modifierState = modifierState,
                shiftActive = shiftActive,
                capsLock = capsLock,
                haptics = haptics,
                smallText = smallText,
                onKey = onKey,
                onModifierTap = onModifierTap,
                onToggleCaps = onToggleCaps,
            )
        }
    }
}

@Composable
private fun ScrollableKeyRow(
    keys: List<KeyDef>,
    modifierState: ModifierState,
    shiftActive: Boolean,
    capsLock: Boolean,
    haptics: Boolean,
    onKey: (TerminalKey) -> Unit,
    onModifierTap: (KeypadModifier, ModifierTap) -> Unit,
    onToggleCaps: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { def ->
            KeyboardKey(
                def = def,
                modifierState = modifierState,
                shiftActive = shiftActive,
                capsLock = capsLock,
                haptics = haptics,
                smallText = false,
                fixedWidth = true,
                onKey = onKey,
                onModifierTap = onModifierTap,
                onToggleCaps = onToggleCaps,
            )
        }
    }
}

@Composable
private fun RowScope.KeyboardKey(
    def: KeyDef,
    modifierState: ModifierState,
    shiftActive: Boolean,
    capsLock: Boolean,
    haptics: Boolean,
    smallText: Boolean,
    onKey: (TerminalKey) -> Unit,
    onModifierTap: (KeypadModifier, ModifierTap) -> Unit,
    onToggleCaps: () -> Unit,
    fixedWidth: Boolean = false,
) {
    val (label, resolved) = resolve(def, shiftActive, capsLock)
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val feedback = LocalHapticFeedback.current

    LaunchedEffect(pressed) {
        if (!pressed || !def.repeatable) return@LaunchedEffect
        delay(400L)
        while (pressed) {
            activateKey(def, resolved, onKey, onModifierTap, onToggleCaps)
            if (haptics) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(55L)
        }
    }

    val visualState = when {
        def.modifier != null -> modifierState.visualState(def.modifier)
        def.caps -> if (capsLock) ModifierState.VisualState.LOCKED else ModifierState.VisualState.OFF
        else -> ModifierState.VisualState.OFF
    }
    val containerColour = when {
        pressed -> MaterialTheme.colorScheme.secondaryContainer
        visualState == ModifierState.VisualState.LOCKED -> MaterialTheme.colorScheme.primary
        visualState == ModifierState.VisualState.ONE_SHOT -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColour = when (visualState) {
        ModifierState.VisualState.LOCKED -> MaterialTheme.colorScheme.onPrimary
        ModifierState.VisualState.ONE_SHOT -> MaterialTheme.colorScheme.onPrimaryContainer
        ModifierState.VisualState.OFF -> MaterialTheme.colorScheme.onSurface
    }
    val baseModifier = if (fixedWidth) Modifier.width(56.dp) else Modifier.weight(def.weight)

    Box(
        modifier = baseModifier
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColour)
            .pointerInput(def, resolved) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        if (def.repeatable) {
                            activateKey(def, resolved, onKey, onModifierTap, onToggleCaps)
                        }
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        if (!def.repeatable) activateKey(def, resolved, onKey, onModifierTap, onToggleCaps)
                    },
                    onDoubleTap = {
                        if (def.modifier != null) onModifierTap(def.modifier, ModifierTap.DOUBLE)
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick {
                    activateKey(def, resolved, onKey, onModifierTap, onToggleCaps)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColour,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (smallText) 11.sp else 13.sp,
                fontFamily = if (def.preview) FontFamily.Monospace else null,
            ),
        )
        if (pressed && def.preview) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -with(density) { 54.dp.roundToPx() }),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 6.dp,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun activateKey(
    def: KeyDef,
    resolved: TerminalKey,
    onKey: (TerminalKey) -> Unit,
    onModifierTap: (KeypadModifier, ModifierTap) -> Unit,
    onToggleCaps: () -> Unit,
) {
    when {
        def.modifier != null -> onModifierTap(def.modifier, ModifierTap.SINGLE)
        def.caps -> onToggleCaps()
        else -> onKey(resolved)
    }
}

@Composable
private fun SelectPad(selectionActive: Boolean, onAction: (SelectionAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                ArrowDirection.LEFT to SelectionAction.MOVE_LEFT,
                ArrowDirection.UP to SelectionAction.MOVE_UP,
                ArrowDirection.DOWN to SelectionAction.MOVE_DOWN,
                ArrowDirection.RIGHT to SelectionAction.MOVE_RIGHT,
            ).forEach { (direction, action) ->
                Box(modifier = Modifier.weight(1f)) {
                    PadButton(direction.label, selectionActive) { onAction(action) }
                }
            }
        }
        PadRow(
            listOf("Select", "Select All", "Clear") to listOf(
                SelectionAction.SELECT, SelectionAction.SELECT_ALL, SelectionAction.CLEAR,
            ),
            selectionActive,
            onAction,
        )
        PadRow(
            listOf("Select Word", "Select Line") to listOf(
                SelectionAction.SELECT_WORD, SelectionAction.SELECT_LINE,
            ),
            selectionActive,
            onAction,
        )
        PadRow(
            listOf("Copy", "Paste") to listOf(
                SelectionAction.COPY, SelectionAction.PASTE,
            ),
            selectionActive,
            onAction,
        )
    }
}

@Composable
private fun PadRow(
    labelsAndActions: Pair<List<String>, List<SelectionAction>>,
    selectionActive: Boolean,
    onAction: (SelectionAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labelsAndActions.first.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f)) {
                PadButton(label, selectionActive) { onAction(labelsAndActions.second[index]) }
            }
        }
    }
}

@Composable
private fun PadButton(label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun resolve(def: KeyDef, shiftActive: Boolean, capsLock: Boolean): Pair<String, TerminalKey> {
    val isLetter = def.key is TerminalKey.Character && def.key.char.isLetter()
    val shifted = if (isLetter) capsLock xor shiftActive else shiftActive
    return if (shifted) def.shiftedLabel to def.shiftedKey else def.label to def.key
}
