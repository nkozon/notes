@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package com.ozon.notes

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.ceil
import kotlin.math.min

@Composable
internal fun liquidGlassControlsEnabled(): Boolean =
    Build.VERSION.SDK_INT >= 33 && LocalAdvancedUiStyle.current == AdvancedUiStyle.LIQUID_GLASS

/** Filters only Haze's captured backdrop; text, icons and touch targets stay outside the lens. */
@RequiresApi(33)
@Composable
internal fun Modifier.liquidGlassBackdrop(
    hazeState: HazeState,
    shape: Shape,
    backgroundColor: Color,
    strength: Float,
): Modifier {
    val resources = LocalResources.current
    val factory = remember(resources) {
        val code = resources.openRawResource(R.raw.liquid_glass).bufferedReader().use { it.readText() }
        LiquidGlassFactory(code)
    }
    val frosting = effectiveGlassFrosting(LocalLiquidGlassFrosting.current)
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val style = LiquidGlassStyle(shape, layoutDirection, backgroundColor, frosting, strength)
    return clip(shape)
        .hazeEffect(
            factory = factory,
            input = HazeInput.Sources(hazeState),
            style = style,
            sampling = HazeSampling.FullResolution,
        )
}

private data class LiquidGlassStyle(
    val shape: Shape,
    val layoutDirection: LayoutDirection,
    val background: Color,
    val frosting: Float,
    val strength: Float,
) {
    val blur: Dp get() = (24f * frosting * strength).dp
    // Increase depth with frosting so the lens remains visible through blur.
    val refractionDepth: Float get() = 2.5f + 5f * frosting
    // Padding supplies neighboring pixels to both the blur kernel and the edge lens.
    val padding: Dp get() = blur * 2f + (13f * refractionDepth).dp
}

@RequiresApi(33)
private class LiquidGlassFactory(private val shaderCode: String) : HazeEffectFactory<LiquidGlassStyle> {
    override fun createRenderer(): HazeEffectRenderer<LiquidGlassStyle> = LiquidGlassRenderer(shaderCode)
}

/**
 * A node owns its shader and retained capture. Haze continues to own source selection and geometry.
 * The runtime drawing hook is confined here because Haze 2.0.1's public drawInput() cannot record
 * into a separate GraphicsLayer. No private source layers or reflection are used.
 */
@RequiresApi(33)
private class LiquidGlassRenderer(shaderCode: String) :
    HazeEffectRenderer<LiquidGlassStyle>, HazeEffectRendererRetainedOutput {
    private val shader = RuntimeShader(shaderCode)
    private var graphicsContext: GraphicsContext? = null
    private var layer: GraphicsLayer? = null
    private var capturedInput: HazeEffectInputSnapshot? = null
    private var capturedSize = IntSize.Zero
    private var capturedBackground = Color.Unspecified
    private var capturedOffset = Offset.Unspecified
    private var effectStyle: LiquidGlassStyle? = null
    private var effectSize = IntSize.Zero
    private var effectDensity = 0f
    private var effectBounds = Rect.Zero
    private val outputClip = Path()
    private var retained = false

    override fun HazeEffectLayoutScope.calculateLayerBounds(style: LiquidGlassStyle): Rect =
        modifierBounds.inflate(ceil(style.padding.toPx()))

    override fun HazeEffectDrawScope.draw(style: LiquidGlassStyle) {
        val context = this as HazeEffectRuntimeDrawScope
        // Haze's draw scope has the control's size. Its input is translated into a larger
        // capture layer, so allocate that expanded size and undo the offset only when drawing.
        val targetSize = IntSize(ceil(context.layerSize.width).toInt(), ceil(context.layerSize.height).toInt())
        if (targetSize.width <= 0 || targetSize.height <= 0) return
        val capture = layer?.takeUnless { it.isReleased } ?: run {
            val owner = context.requireGraphicsContext().also { graphicsContext = it }
            owner.createGraphicsLayer().also { layer = it; retained = false }
        }
        if (context.hasDrawableInput && (!retained || capturedInput != context.inputSnapshot ||
                capturedSize != targetSize || capturedBackground != style.background || capturedOffset != context.layerOffset)) {
            capture.record(targetSize) {
                drawRect(style.background)
                with(context) { this@record.drawInput() }
            }
            capturedInput = context.inputSnapshot
            capturedSize = targetSize
            capturedBackground = style.background
            capturedOffset = context.layerOffset
            retained = true
        }
        if (!retained || capturedSize != targetSize) {
            drawRect(style.background, size = context.modifierSize)
            return
        }
        if (effectStyle != style || effectSize != targetSize || effectDensity != density || effectBounds != modifierBounds) {
            val body = modifierBounds
            val outline = style.shape.createOutline(body.size, style.layoutDirection, this)
            outputClip.reset()
            when (outline) {
                is Outline.Rectangle -> outputClip.addRect(outline.rect)
                is Outline.Rounded -> outputClip.addRoundRect(outline.roundRect)
                is Outline.Generic -> outputClip.addPath(outline.path)
            }
            val cornerRadii = when (outline) {
                is Outline.Rounded -> with(outline.roundRect) {
                    floatArrayOf(
                        min(topLeftCornerRadius.x, topLeftCornerRadius.y),
                        min(topRightCornerRadius.x, topRightCornerRadius.y),
                        min(bottomRightCornerRadius.x, bottomRightCornerRadius.y),
                        min(bottomLeftCornerRadius.x, bottomLeftCornerRadius.y),
                    )
                }
                else -> FloatArray(4)
            }
            shader.setFloatUniform("body", body.left, body.top, body.width, body.height)
            shader.setFloatUniform("corners", cornerRadii)
            shader.setFloatUniform("textureSize", targetSize.width.toFloat(), targetSize.height.toFloat())
            shader.setFloatUniform("edgeWidth", min(11.dp.toPx(), min(body.width, body.height) / 5f).coerceAtLeast(1f))
            shader.setFloatUniform("strength", style.strength)
            shader.setFloatUniform("refractionDepth", style.refractionDepth)
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
            val radius = style.blur.toPx()
            capture.renderEffect = (if (radius > 0.01f) {
                RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            } else lens).asComposeRenderEffect()
            effectStyle = style
            effectSize = targetSize
            effectDensity = density
            effectBounds = body
        }
        // Keep the blur kernel's padding available to the lens, but never paint it around
        // the control. Clip on the canvas as well as the outer modifier's graphics layer.
        clipPath(outputClip) {
            translate(-context.layerOffset.x, -context.layerOffset.y) {
                drawLayer(capture)
            }
        }
    }

    override fun canDrawRetainedOutput(): Boolean = retained && layer?.isReleased == false

    override fun invalidateRetainedOutput() {
        retained = false
    }

    override fun clearRetainedOutput() {
        layer?.let { graphicsContext?.releaseGraphicsLayer(it) }
        layer = null
        graphicsContext = null
        capturedInput = null
        effectStyle = null
        retained = false
    }

    override fun onTrimMemory(level: TrimMemoryLevel) = clearRetainedOutput()
    override fun dispose() = clearRetainedOutput()
}
