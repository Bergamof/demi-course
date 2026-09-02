package com.demicourse.seance.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.demicourse.seance.ui.FieldKey
import com.demicourse.seance.ui.SheetState
import com.demicourse.seance.ui.fieldOrder

/**
 * Drives the editor sheet's keyboard flow, mirroring the prototype: plain Enter (or the
 * keyboard's "next" action) moves to the next field and selects its text — or submits if it's
 * the last field — Ctrl/Cmd+Enter always submits, and Escape closes. Tab also advances, but
 * (matching the prototype, where Tab was left to native focus order rather than wired up) it
 * does not select the destination's text.
 *
 * Recreated per [openKey] so a freshly opened sheet starts with no field focused/selected.
 */
class SheetFocusController {
    val requesters = mutableMapOf<FieldKey, FocusRequester>()
    var focusedField by mutableStateOf<FieldKey?>(null)
    val pendingSelectAll = mutableStateMapOf<FieldKey, Boolean>()

    fun requesterFor(key: FieldKey): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun moveTo(key: FieldKey, selectAll: Boolean) {
        if (selectAll) pendingSelectAll[key] = true
        runCatching { requesterFor(key).requestFocus() }
    }

    fun advance(sheet: SheetState, selectAll: Boolean, onSubmit: () -> Unit) {
        val order = fieldOrder(sheet)
        val idx = order.indexOf(focusedField)
        if (idx == -1 || idx == order.lastIndex) onSubmit() else moveTo(order[idx + 1], selectAll)
    }

    fun focusFirst(sheet: SheetState) {
        fieldOrder(sheet).firstOrNull()?.let { moveTo(it, selectAll = true) }
    }

    /** Returns true if the key event was consumed. */
    fun handleKeyEvent(event: KeyEvent, sheet: SheetState, onSubmit: () -> Unit, onClose: () -> Unit): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        return when {
            event.key == Key.Escape -> { onClose(); true }
            isEnter && (event.isCtrlPressed || event.isMetaPressed) -> { onSubmit(); true }
            event.key == Key.Tab -> { advance(sheet, selectAll = false, onSubmit); true }
            isEnter -> { advance(sheet, selectAll = true, onSubmit); true }
            else -> false
        }
    }
}

@Composable
fun rememberSheetFocusController(openKey: Any?): SheetFocusController =
    remember(openKey) { SheetFocusController() }
