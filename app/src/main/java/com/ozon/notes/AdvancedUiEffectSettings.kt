package com.ozon.notes

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun AdvancedUiEffectSettings(
    advancedUiEnabled: Boolean,
    style: AdvancedUiStyle,
    frosting: Float,
    onStyleChange: (AdvancedUiStyle) -> Unit,
    onFrostingChange: (Float) -> Unit,
) {
    val supportsLiquidGlass = Build.VERSION.SDK_INT >= 33
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeModeItem(
                label = "Simple blur",
                selected = style == AdvancedUiStyle.SIMPLE_BLUR,
                onClick = { onStyleChange(AdvancedUiStyle.SIMPLE_BLUR) },
                modifier = Modifier.weight(1f),
                enabled = advancedUiEnabled,
            )
            ThemeModeItem(
                label = "Liquid glass",
                selected = style == AdvancedUiStyle.LIQUID_GLASS,
                onClick = { onStyleChange(AdvancedUiStyle.LIQUID_GLASS) },
                modifier = Modifier.weight(1f),
                enabled = advancedUiEnabled && supportsLiquidGlass,
            )
        }
        if (!supportsLiquidGlass) {
            Text(
                "Liquid glass requires Android 13 or newer. This device uses simple blur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (style == AdvancedUiStyle.LIQUID_GLASS) {
            val value = normalizedGlassFrosting(frosting)
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                Text("Glass frosting strength", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = value,
                    onValueChange = { onFrostingChange(normalizedGlassFrosting(it)) },
                    enabled = advancedUiEnabled && supportsLiquidGlass,
                    valueRange = 0f..MaxLiquidGlassFrosting,
                    steps = 6,
                    modifier = Modifier.semantics {
                        contentDescription = "Glass frosting strength"
                        stateDescription = "Level ${(value * 10).roundToInt() + 1} of 8"
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Clear", style = MaterialTheme.typography.bodySmall)
                    Text("Frosted", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

