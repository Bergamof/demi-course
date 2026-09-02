package com.demicourse.seance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.demicourse.seance.ui.FieldKey
import com.demicourse.seance.ui.SheetState
import com.demicourse.seance.ui.theme.LocalSeanceColors

/**
 * A single editor input, styled as the prototype's bordered field boxes (not Material's
 * outlined/filled chrome), wired into a [SheetFocusController] for the Tab/Enter focus chain
 * and the open/advance "select all" behavior.
 */
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

    val fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
    val style = TextStyle(color = colors.fg, fontSize = fontSize, fontFamily = fontFamily, textAlign = textAlign)

    val core: @Composable () -> Unit = {
        BasicTextField(
            value = tfv,
            onValueChange = { new ->
                tfv = new
                if (new.text != value) onValueChange(new.text)
            },
            modifier = Modifier
                .focusRequester(controller.requesterFor(field))
                .onFocusChanged { if (it.isFocused) controller.focusedField = field }
                .fillMaxWidth(),
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                // Free-text fields (the template name) get sentence capitalization; every other
                // field is a pace/distance/duration and must not turn into a text keyboard.
                capitalization = if (keyboardType == KeyboardType.Text) {
                    KeyboardCapitalization.Sentences
                } else {
                    KeyboardCapitalization.None
                },
                imeAction = ImeAction.Next,
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
            modifier = modifier
                .height(height)
                .background(colors.field, RoundedCornerShape(12.dp))
                .border(1.dp, colors.line2, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { core() }
    } else {
        Box(modifier = modifier) { core() }
    }
}
