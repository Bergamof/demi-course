package com.demicourse.seance.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demicourse.domain.DraftErrors
import com.demicourse.domain.FieldKey
import com.demicourse.domain.SheetState
import com.demicourse.domain.fieldOrder
import com.demicourse.seance.ui.theme.LocalSeanceColors
import kotlinx.coroutines.delay

/**
 * A single editor input, styled as the prototype's bordered field boxes (not Material's
 * outlined/filled chrome), wired into a [SheetFocusController] for the Tab/Enter focus chain
 * and the open/advance "select all" behavior.
 *
 * A focused field also keeps itself inside the sheet's scroll viewport, so a field low in the
 * editor isn't left hidden behind the soft keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeanceTextField(
    controller: SheetFocusController,
    field: FieldKey,
    sheet: SheetState,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    monospace: Boolean = true,
    fontSize: TextUnit = 16.sp,
    textAlign: TextAlign = TextAlign.Unspecified,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    boxed: Boolean = true,
    height: Dp = 52.dp,
) {
    val colors = LocalSeanceColors.current
    var tfv by remember(controller, field) { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != tfv.text) tfv = TextFieldValue(value, TextRange(value.length))
    }
    val pendingSelect = controller.pendingSelectAll[field]
    LaunchedEffect(pendingSelect) {
        if (pendingSelect == true) {
            tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
            controller.pendingSelectAll[field] = false
        }
    }

    // The soft keyboard shrinks the sheet's scroll viewport (see `imePadding` in BottomSheetHost),
    // which can leave an already-focused field below the fold. Reading the IME inset here
    // re-runs the effect on every frame of the keyboard animation, so the request fires once the
    // keyboard has settled rather than mid-slide.
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember(controller, field) { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            delay(60)
            bringIntoView.bringIntoView()
        }
    }

    val isLastField = fieldOrder(sheet).lastOrNull() == field
    val borderColor =
        when {
            hasError(sheet.errors, field) -> colors.danger
            focused -> colors.accent
            else -> colors.line2
        }
    val borderWidth = if (focused || hasError(sheet.errors, field)) 2.dp else 1.dp

    val fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
    val style = TextStyle(color = colors.fg, fontSize = fontSize, fontFamily = fontFamily, textAlign = textAlign)

    val core: @Composable () -> Unit = {
        BasicTextField(
            value = tfv,
            onValueChange = { new ->
                tfv = new
                if (new.text != value) onValueChange(new.text)
            },
            modifier =
                Modifier
                    .semantics { contentDescription = fieldLabel(field) }
                    .focusRequester(controller.requesterFor(field))
                    .bringIntoViewRequester(bringIntoView)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) controller.focusedField = field
                    }.fillMaxWidth(),
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    // Free-text fields (the template name) get sentence capitalization; every other
                    // field is a pace/distance/duration and must not turn into a text keyboard.
                    capitalization =
                        if (keyboardType == KeyboardType.Text) {
                            KeyboardCapitalization.Sentences
                        } else {
                            KeyboardCapitalization.None
                        },
                    imeAction = if (isLastField) ImeAction.Done else ImeAction.Next,
                ),
            keyboardActions = KeyboardActions(onNext = { controller.advance(sheet, selectAll = true, onSubmit) }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (tfv.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(text = placeholder, color = colors.muted3, fontSize = fontSize, fontFamily = fontFamily)
                    }
                    inner()
                }
            },
        )
    }

    if (boxed) {
        Box(
            modifier =
                modifier
                    .height(height)
                    .background(colors.field, RoundedCornerShape(12.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { core() }
    } else {
        Box(modifier = modifier) { core() }
    }
}

/** What a screen reader says for a field: the visible label sits in a neighbouring [Text], not on the field. */
private fun fieldLabel(field: FieldKey): String =
    when (field) {
        FieldKey.TEMPLATE_NAME -> "Nom du modèle"
        FieldKey.VALUE -> "Longueur de l’étape"
        FieldKey.PACE -> "Allure"
        FieldKey.PACE_MAX -> "Allure maximale"
        FieldKey.REPS -> "Nombre de répétitions"
        FieldKey.REC_DUR -> "Durée de récupération"
        FieldKey.REC_PACE -> "Allure de récupération"
        FieldKey.REC_PACE_MAX -> "Allure de récupération maximale"
    }

private fun hasError(
    errors: DraftErrors,
    field: FieldKey,
): Boolean =
    when (field) {
        FieldKey.TEMPLATE_NAME -> errors.templateName != null
        FieldKey.VALUE -> errors.value != null
        FieldKey.PACE -> errors.pace != null
        FieldKey.PACE_MAX -> errors.paceMax != null
        FieldKey.REPS -> errors.reps != null
        FieldKey.REC_DUR -> errors.recDur != null
        FieldKey.REC_PACE, FieldKey.REC_PACE_MAX -> errors.recPace != null
    }
