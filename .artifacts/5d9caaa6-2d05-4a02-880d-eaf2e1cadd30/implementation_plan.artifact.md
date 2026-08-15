# Drawing Note Screen Tablet UI Enhancements

Improve the `DrawingNoteScreen` behavior in tablet (split-screen) mode to handle status bar interaction and add a rounded corner to the detail pane.

## User Review Required

> [!NOTE]
> The rounded corner (28.dp) will only be applied to the top-left of the drawing screen when in split-screen mode on tablets. In fullscreen mode, it will remain square and go edge-to-edge.

## Proposed Changes

### [Drawing Note Screen](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)

#### [MODIFY] [DrawingNoteScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)
- Update the outer `Box` modifier:
    - Apply `Modifier.windowInsetsPadding(WindowInsets.statusBars)` only when `isNormalTablet` is true.
    - Apply `.clip(RoundedCornerShape(topStart = 28.dp))` only when `isNormalTablet` is true.
- Update the inner `Box` (Canvas container) modifier:
    - Add `.clipToBounds()` when `isNormalTablet` is true to prevent drawings from bleeding into the status bar area.
    - Keep it unclipped in fullscreen mode as requested.
- Adjust `SystemBarGradients` usage:
    - Hide the top gradient when `isNormalTablet` is true, as the screen is already padded below the status bar.

## Verification Plan

### Manual Verification
1.  **Tablet Split-Screen:**
    - Open a drawing note on the right side.
    - Verify the top-left corner is rounded.
    - Verify the white background starts below the status bar.
    - Try drawing at the very top; verify strokes do NOT appear in the status bar area (clipped).
2.  **Tablet Fullscreen:**
    - Tap the fullscreen toggle in the drawing note.
    - Verify the rounded corner disappears.
    - Verify the white background goes behind the status bar.
    - Verify strokes CAN be drawn over the status bar area (unclipped).
3.  **Phone Mode:**
    - Verify it still behaves correctly (edge-to-edge, square corners).
