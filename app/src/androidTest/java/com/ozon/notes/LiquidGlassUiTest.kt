package com.ozon.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ozon.notes.drawing.ui.DrawingControlsTheme
import com.ozon.notes.drawing.ui.ToolbarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class LiquidGlassUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun buttonsSelectExactlyOneEffectAndSliderUpdatesFrosting() {
        var style by mutableStateOf(AdvancedUiStyle.SIMPLE_BLUR)
        var frosting by mutableFloatStateOf(DefaultLiquidGlassFrosting)
        compose.setContent {
            MaterialTheme {
                AdvancedUiEffectSettings(true, style, frosting, { style = it }, { frosting = it })
            }
        }
        compose.onNodeWithText("Simple blur").assertIsSelected()
        compose.onNodeWithText("Liquid glass").assertIsNotSelected().performClick()
        compose.onNodeWithText("Simple blur").assertIsNotSelected()
        compose.onNodeWithText("Liquid glass").assertIsSelected()
        compose.onNodeWithContentDescription("Glass frosting strength")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.37f) }
        compose.runOnIdle { assertEquals(0.4f, frosting, 0.01f) }
        compose.onNodeWithContentDescription("Glass frosting strength")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..0.5f, 4))
        compose.onNodeWithText("Glass frosting strength").assertExists()
        // Selecting the active button keeps it selected, just like the theme buttons.
        compose.onNodeWithText("Liquid glass").performClick()
        compose.onNodeWithText("Liquid glass").assertIsSelected()
        compose.onNodeWithText("Simple blur").performClick()
        compose.onNodeWithText("Simple blur").assertIsSelected()
        compose.onNodeWithText("Liquid glass").assertIsNotSelected()
    }

    @Test
    fun effectAndFrostingSurviveRepositoryRecreation() = runBlocking {
        RepositoryTestEnvironment().use { environment ->
            val repository = environment.repository
            assertEquals(AdvancedUiStyle.SIMPLE_BLUR, repository.getAdvancedUiStyle().first())
            repository.setAdvancedUi(true)
            repository.setAdvancedUiStyle(AdvancedUiStyle.LIQUID_GLASS)
            repository.setLiquidGlassFrosting(0.4f)
            val reopened = RoomNoteRepository(environment.context, environment.database)
            try {
                assertEquals(true, reopened.getAdvancedUi().first())
                assertEquals(AdvancedUiStyle.LIQUID_GLASS, reopened.getAdvancedUiStyle().first())
                assertEquals(0.4f, reopened.getLiquidGlassFrosting().first(), 0.001f)
                reopened.setAdvancedUi(false)
                // Turning the master switch off preserves the chosen material and frosting.
                assertEquals(AdvancedUiStyle.LIQUID_GLASS, reopened.getAdvancedUiStyle().first())
                assertEquals(0.4f, reopened.getLiquidGlassFrosting().first(), 0.001f)
            } finally {
                reopened.getDropboxSyncEngine().close()
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun lensRefractsBackdropFrostingSoftensItAndForegroundStaysSharp() {
        var strength by mutableFloatStateOf(0f)
        var frosting by mutableFloatStateOf(0f)
        compose.setContent {
            MaterialTheme {
                val haze = rememberHazeState()
                CompositionLocalProvider(LocalLiquidGlassFrosting provides frosting) {
                    Box(Modifier.size(240.dp, 130.dp)) {
                        Canvas(Modifier.fillMaxSize().advancedUiSource(haze, true)) {
                            drawRect(Color.Cyan)
                            val stripe = 3.dp.toPx()
                            for (i in 0..(size.width / stripe).toInt() step 2) {
                                drawRect(Color.Red, Offset(i * stripe, 0f), Size(stripe, size.height))
                            }
                        }
                        Box(
                            Modifier.offset(30.dp, 30.dp).size(180.dp, 72.dp).testTag("glass")
                                .liquidGlassBackdrop(haze, RoundedCornerShape(24.dp), Color.White, strength),
                        ) {
                            Box(Modifier.align(Alignment.Center).size(12.dp).background(Color.Magenta))
                        }
                    }
                }
            }
        }
        val reference = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        compose.runOnIdle { strength = 1f }
        val clear = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val scale = clear.width / 180f
        var changed = 0
        var samples = 0
        // Sample inside the straight edge, away from the rounded corners.
        for (y in (30 * scale).toInt()..(42 * scale).toInt()) {
            for (x in (3 * scale).toInt()..(9 * scale).toInt()) {
                if (abs(reference[x, y].red - clear[x, y].red) > 0.15f) changed++
                samples++
            }
        }
        assertTrue("The lens must distort captured stripes near the edge", changed > samples / 5)
        assertEquals(Color.Magenta, clear[clear.width / 2, clear.height / 2])
        compose.runOnIdle { frosting = MaxLiquidGlassFrosting }
        val frosted = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        assertTrue("Frosting must reduce backdrop contrast", contrast(frosted) < contrast(clear) * 0.5f)
        assertEquals(Color.Magenta, frosted[frosted.width / 2, frosted.height / 2])
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun paddedBackdropIsAlignedAndCannotSpillOutsideThePill() {
        var showGlass by mutableStateOf(false)
        var frosting by mutableFloatStateOf(0f)
        var strength by mutableFloatStateOf(0f)
        compose.setContent {
            MaterialTheme {
                val haze = rememberHazeState()
                CompositionLocalProvider(LocalLiquidGlassFrosting provides frosting) {
                    Box(Modifier.size(260.dp, 160.dp).testTag("scene")) {
                        Canvas(Modifier.fillMaxSize().advancedUiSource(haze, true)) {
                            drawRect(Color(0xFF102030))
                            // Unequal two-dimensional features expose capture translation errors.
                            drawRect(Color.Red, Offset(41.dp.toPx(), 12.dp.toPx()), Size(23.dp.toPx(), 130.dp.toPx()))
                            drawRect(Color.Cyan, Offset(90.dp.toPx(), 39.dp.toPx()), Size(115.dp.toPx(), 13.dp.toPx()))
                            drawRect(Color.Green, Offset(11.dp.toPx(), 88.dp.toPx()), Size(220.dp.toPx(), 17.dp.toPx()))
                        }
                        Box(
                            Modifier.offset(30.dp, 30.dp).size(180.dp, 72.dp).then(
                                if (showGlass) Modifier.liquidGlassBackdrop(
                                    haze, RoundedCornerShape(36.dp), Color.White, strength,
                                ) else Modifier,
                            ),
                        )
                    }
                }
            }
        }
        val reference = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        compose.runOnIdle { showGlass = true }
        val aligned = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        val scale = reference.width / 260f
        // An unfrosted lens at zero strength must be exactly the backdrop under this location.
        for (y in 45..85 step 4) {
            for (x in 65..175 step 4) {
                val px = (x * scale).toInt()
                val py = (y * scale).toInt()
                assertTrue("Backdrop shifted at ($x,$y)", colorDifference(reference[px, py], aligned[px, py]) < 0.03f)
            }
        }
        compose.runOnIdle { frosting = MaxLiquidGlassFrosting; strength = 1f }
        val frosted = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        for (y in 5..150 step 3) {
            for (x in 5..250 step 3) {
                // Include the area inside the bounding box but outside its rounded corners.
                val outside = x < 28 || x > 212 || y < 28 || y > 104 ||
                    (x < 38 && y < 38) || (x > 202 && y < 38)
                if (outside) {
                    val px = (x * scale).toInt()
                    val py = (y * scale).toInt()
                    assertTrue("Glass leaked at ($x,$y)", colorDifference(reference[px, py], frosted[px, py]) < 0.03f)
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun popupGlassSamplesAndRefreshesTheScreenBehindItsWindow() {
        var backdropColor by mutableStateOf(Color.Red)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val haze = rememberHazeState()
                CompositionLocalProvider(
                    LocalHazeState provides haze,
                    LocalAdvancedUiEnabled provides true,
                    LocalAdvancedUiStyle provides AdvancedUiStyle.LIQUID_GLASS,
                    LocalLiquidGlassFrosting provides 0.3f,
                ) {
                    Box(Modifier.size(360.dp, 260.dp)) {
                        Canvas(Modifier.fillMaxSize().advancedUiSource(haze, true)) {
                            drawRect(backdropColor)
                        }
                        Box(Modifier.offset(30.dp, 30.dp).size(1.dp)) {
                            AdvancedUiDropdownMenu(
                                expanded = true,
                                onDismissRequest = {},
                                modifier = Modifier.width(180.dp).testTag("popup"),
                            ) {
                                Text("Glass menu", Modifier.padding(16.dp))
                                Spacer(Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val red = compose.onNodeWithTag("popup").captureToImage().toPixelMap()
        compose.runOnIdle { backdropColor = Color.Blue }
        compose.mainClock.advanceTimeBy(500)
        val blue = compose.onNodeWithTag("popup").captureToImage().toPixelMap()
        val redSample = red[red.width / 2, red.height * 3 / 4]
        val blueSample = blue[blue.width / 2, blue.height * 3 / 4]
        assertTrue("The popup must capture the red screen", redSample.red > blueSample.red + 0.3f)
        assertTrue("The popup must refresh after background changes", blueSample.blue > redSample.blue + 0.3f)
    }

    @Test
    fun drawingToolbarHasDarkUnselectedIconsAndPrimarySelectionInDarkAppTheme() {
        var expectedPrimary = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                expectedPrimary = MaterialTheme.colorScheme.primary
                Surface(color = Color.Black) {
                    DrawingControlsTheme {
                        // A custom translucent surface used to inherit the dark app's pale content.
                        Surface(color = Color(0xFFE9EEE9)) {
                            Row {
                                Box(Modifier.size(48.dp).testTag("selected-tool"), contentAlignment = Alignment.Center) {
                                    ToolbarItem(DrawingTool.PEN, rememberVectorPainter(Icons.Rounded.Edit), true) {}
                                }
                                Box(Modifier.size(48.dp).testTag("unselected-tool"), contentAlignment = Alignment.Center) {
                                    ToolbarItem(DrawingTool.PEN, rememberVectorPainter(Icons.Rounded.Edit), false) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        val selected = compose.onNodeWithTag("selected-tool").captureToImage().toPixelMap()
        val unselected = compose.onNodeWithTag("unselected-tool").captureToImage().toPixelMap()
        val scale = selected.width / 48f
        // Below the 18 dp glyph but inside the 30 dp circular selection surface.
        val selectionFill = selected[selected.width / 2, selected.height / 2 + (12 * scale).toInt()]
        assertTrue("Selected tool must use the primary fill", colorDifference(expectedPrimary, selectionFill) < 0.03f)
        var darkPixels = 0
        for (y in 0 until unselected.height) {
            for (x in 0 until unselected.width) {
                val color = unselected[x, y]
                if (color.red < 0.3f && color.green < 0.3f && color.blue < 0.3f) darkPixels++
            }
        }
        assertTrue("Unselected glyph must remain visibly dark", darkPixels > unselected.width * unselected.height / 100)
    }

    private fun colorDifference(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun contrast(pixels: PixelMap): Float {
        val scale = pixels.width / 180f
        val y = (24 * scale).toInt()
        val values = ((35 * scale).toInt()..(145 * scale).toInt()).map { pixels[it, y].red }
        val mean = values.average().toFloat()
        return values.sumOf { abs(it - mean).toDouble() }.toFloat() / values.size
    }
}
