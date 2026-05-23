package com.phonepvr.friends.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Masked date entry component. The model is the raw digit string the user
 * typed (0–8 chars), the view shows it formatted as `DD/MM/YYYY` with slashes
 * inserted automatically.
 *
 * Year-optional mode accepts a 4-digit `DDMM` or an 8-digit `DDMMYYYY`. The
 * caller decides how to interpret an in-between length (5–7 digits) — usually
 * by showing a "Keep typing or finish without a year" supporting message
 * derived from [parseDateDigits].
 */
@Composable
fun DateTextField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowYearOptional: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = digits,
        onValueChange = { input ->
            onDigitsChange(input.filter { it.isDigit() }.take(8))
        },
        label = { Text(label) },
        placeholder = {
            Text(if (allowYearOptional) "DD/MM/YYYY (year optional)" else "DD/MM/YYYY")
        },
        singleLine = true,
        visualTransformation = DdMmYyyyTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier,
    )
}

private val DdMmYyyyTransformation = VisualTransformation { text ->
    val digits = text.text.take(8)
    val transformed = buildString {
        for (i in digits.indices) {
            append(digits[i])
            if (i == 1 || i == 3) append('/')
        }
    }
    TransformedText(AnnotatedString(transformed), DdMmYyyyOffsetMapping)
}

/**
 * Raw indices 0..8 map to transformed indices 0..10. Two `/` characters are
 * inserted: one after raw[1] (display index 2 → 3+) and one after raw[3]
 * (display index 4 → 5+).
 */
private val DdMmYyyyOffsetMapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = when {
        offset <= 1 -> offset
        offset <= 3 -> offset + 1
        else -> offset + 2
    }
    override fun transformedToOriginal(offset: Int): Int = when {
        offset <= 2 -> offset
        offset <= 5 -> offset - 1
        else -> offset - 2
    }
}
