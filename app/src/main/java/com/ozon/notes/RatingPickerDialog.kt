package com.ozon.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun RatingPickerDialog(
    rating: Float,
    onRatingChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = onDismiss
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isEditingRating by remember { mutableStateOf(false) }
    var editingRatingValue by remember {
        val initialDisplay = (rating * 10).roundToInt() / 10f
        val initialStr = formatRating(initialDisplay)
        mutableStateOf(
            TextFieldValue(
                text = initialStr,
                selection = TextRange(0, initialStr.length)
            )
        )
    }
    val ratingFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditingRating) {
        if (isEditingRating) {
            delay(50)
            ratingFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val closeRatingPicker: (() -> Unit) -> Unit = { close ->
        if (isEditingRating) {
            editingRatingValue.text.toFloatOrNull()?.let {
                onRatingChange(((it * 10).roundToInt() / 10f).coerceIn(0f, 10f))
            }
        }
        isEditingRating = false
        keyboardController?.hide()
        close()
    }
    AlertDialog(
        onDismissRequest = { closeRatingPicker(onDismiss) },
        title = { Text("Rating") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEditingRating) {
                    val commitRating: () -> Unit = {
                        val f = editingRatingValue.text.toFloatOrNull()
                        if (f != null) {
                            onRatingChange(((f * 10).roundToInt() / 10f).coerceIn(0f, 10f))
                        }
                        isEditingRating = false
                        keyboardController?.hide()
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(160.dp)
                                .height(152.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                    value = editingRatingValue,
                                    onValueChange = { newValue ->
                                        val sanitized = newValue.text.replace(',', '.')
                                        if (sanitized.isEmpty() || Regex("""^(10(\.0?)?|[0-9](\.[0-9]?)?|\.[0-9]?)$""").matches(sanitized)) {
                                            editingRatingValue = newValue.copy(text = sanitized)
                                            sanitized.toFloatOrNull()?.let { f ->
                                                onRatingChange(((f * 10).roundToInt() / 10f).coerceIn(0f, 10f))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { commitRating() }
                                    ),
                                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(ratingFocusRequester)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            isEditingRating = false
                                            keyboardController?.hide()
                                        }
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                    }

                                    FilledTonalIconButton(
                                        onClick = commitRating,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Confirm rating",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "/ 10",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ScoreWheelPicker(
                            score = rating,
                            onScoreChange = onRatingChange,
                            onScoreClick = {
                                val currentStr = formatRating(rating)
                                editingRatingValue = TextFieldValue(
                                    text = currentStr,
                                    selection = TextRange(0, currentStr.length)
                                )
                                isEditingRating = true
                            },
                            modifier = Modifier.width(160.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "/ 10",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Text(
                        text = "Scroll up/down to adjust • Tap score to type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { closeRatingPicker(onConfirm) }) { Text("Done") }
        }
    )
}
