package com.ozon.notes

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp

private val glassShadowMaskPass = ThreadLocal<Boolean>()

/** Separators remain visible in the foreground but are omitted from its shadow mask. */
internal fun Modifier.withoutGlassContentShadow(): Modifier = drawWithContent {
    if (glassShadowMaskPass.get() != true) drawContent()
}

/** Shadows the foreground without outlining the translucent glass background. */
@RequiresApi(33)
@Composable
internal fun Modifier.liquidGlassContentShadow(strength: Float): Modifier {
    val contentLayer = rememberGraphicsLayer()
    val shadowLayer = rememberGraphicsLayer()
    return drawWithCache {
        // The glass tint is below 30% opacity at these frosting levels. Subtract that
        // baseline so the shadow mask contains the text, icons and filled controls only.
        val mask = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0.5f * strength, -38.25f * strength,
            )))
        )
        val blur = RenderEffect.createBlurEffect(1.5.dp.toPx(), 1.5.dp.toPx(), mask, Shader.TileMode.DECAL)
        val shadow = RenderEffect.createOffsetEffect(0f, 1.dp.toPx(), blur)
        shadowLayer.renderEffect = shadow.asComposeRenderEffect()
        onDrawWithContent {
            if (glassShadowMaskPass.get() == true) {
                // A containing glass surface is already recording its shadow mask.
                drawContent()
            } else {
                contentLayer.record { this@onDrawWithContent.drawContent() }
                try {
                    glassShadowMaskPass.set(true)
                    shadowLayer.record { this@onDrawWithContent.drawContent() }
                } finally {
                    glassShadowMaskPass.remove()
                }
                drawLayer(shadowLayer)
                drawLayer(contentLayer)
            }
        }
    }
}
