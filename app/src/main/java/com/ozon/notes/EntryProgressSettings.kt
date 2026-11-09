package com.ozon.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class EntryProgressStatus { NONE, WATCHING, PAUSED }

internal data class EntryProgressDraft(
    val status: EntryProgressStatus,
    val current: String,
    val total: String,
    val unit: String
)

private val ProgressUnitPresets = listOf("episodes", "pages", "chapters")

@Composable
internal fun EntryProgressSettings(
    currentSectionName: String,
    value: EntryProgressDraft,
    onValueChange: (EntryProgressDraft) -> Unit
) {
    var showCustomUnitDialog by remember { mutableStateOf(false) }
    val hasCustomUnit = ProgressUnitPresets.none { value.unit.equals(it, ignoreCase = true) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProgressToggle(
                    checked = value.status == EntryProgressStatus.WATCHING,
                    onCheckedChange = { checked ->
                        onValueChange(value.copy(status = if (checked) EntryProgressStatus.WATCHING else EntryProgressStatus.NONE))
                    },
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(currentSectionName, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                ProgressToggle(
                    checked = value.status == EntryProgressStatus.PAUSED,
                    onCheckedChange = { checked ->
                        onValueChange(value.copy(status = if (checked) EntryProgressStatus.PAUSED else EntryProgressStatus.NONE))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Paused", fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(
                visible = value.status != EntryProgressStatus.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Progress",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProgressNumberField(
                            value = value.current,
                            onValueChange = { onValueChange(value.copy(current = it)) },
                            placeholder = "Current",
                            imeAction = ImeAction.Next,
                            modifier = Modifier.weight(1f)
                        )
                        ProgressNumberField(
                            value = value.total,
                            onValueChange = { onValueChange(value.copy(total = it)) },
                            placeholder = "Total",
                            imeAction = ImeAction.Done,
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (hasCustomUnit) "Unit · ${value.unit}" else "Unit",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProgressUnitPresets.forEach { preset ->
                                ProgressToggle(
                                    checked = value.unit.equals(preset, ignoreCase = true),
                                    onCheckedChange = { onValueChange(value.copy(unit = preset)) },
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        preset.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            ProgressToggle(
                                checked = hasCustomUnit,
                                onCheckedChange = { showCustomUnitDialog = true },
                                compact = true,
                                modifier = Modifier.weight(0.6f)
                                    .semantics { contentDescription = "Custom progress unit" }
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    val current = value.current.toIntOrNull() ?: 0
                    val total = value.total.toIntOrNull()
                    if (total != null && total > 0) {
                        val left = (total - current).coerceAtLeast(0)
                        Text(
                            text = if (left == 0) "Completed" else "$left ${value.unit} left ($current/$total)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showCustomUnitDialog) {
        CustomProgressUnitDialog(
            unit = value.unit,
            onDismiss = { showCustomUnitDialog = false },
            onConfirm = {
                onValueChange(value.copy(unit = it))
                showCustomUnitDialog = false
            }
        )
    }
}

@Composable
private fun ProgressToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    val radius by animateDpAsState(
        targetValue = if (checked) 28.dp else if (compact) 18.dp else 16.dp,
        label = "progressToggleRadius"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            checked -> MaterialTheme.colorScheme.primary
            compact -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        label = "progressToggleContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            checked -> MaterialTheme.colorScheme.onPrimary
            compact -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        label = "progressToggleContent"
    )
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.heightIn(min = if (compact) 36.dp else 48.dp)
                .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun ProgressNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    TextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onValueChange(it) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.heightIn(min = 64.dp)
            .semantics { contentDescription = "$placeholder progress" },
        shape = RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
private fun CustomProgressUnitDialog(unit: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(unit, TextRange(0, unit.length))) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom unit") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Unit name") },
                placeholder = { Text("e.g. volumes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    name.text.trim().takeIf { it.isNotEmpty() }?.let(onConfirm)
                })
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.trim()) }, enabled = name.text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
