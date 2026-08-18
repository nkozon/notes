# Implementation Plan - Processing Drawing Part

This plan consolidates pending UI enhancements and bug fixes for the drawing functionality in the Notes app. It focuses on improving the tablet experience and refining the toolbar organization.

## User Review Required

> [!NOTE]
> - **Status Bar Style**: I will set the status bar to a dark style (light icons) on the drawing screen as requested in previous bug fix plans. This is common for drawing apps.
> - **Toolbar Reorganization**: Undo, Redo, and Paste will be moved from the main bottom toolbar to a new "Utility Toolbar" located just below the top header.

## Proposed Changes

### [Drawing Note Screen](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)

#### [MODIFY] [DrawingNoteScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)

1.  **Status Bar Style:**
    *   Update `LaunchedEffect` to use `SystemBarStyle.dark` when `shouldBeImmersive` is true, ensuring light icons on a dark/transparent background.

2.  **Tablet UI Enhancements (Split-Screen):**
    *   Increase `RoundedCornerShape` to `28.dp` (from `12.dp`) for the outer `Box` in tablet mode.
    *   Ensure the outer `Box` uses `Modifier.windowInsetsPadding(WindowInsets.statusBars)` when in tablet mode.
    *   Add `.clipToBounds()` to the Canvas `Box` when in tablet mode to prevent drawings from bleeding into the status bar area.
    *   Hide the top gradient of `SystemBarGradients` when `isNormalTablet` is true.

3.  **Toolbar Reorganization:**
    *   Implement `DrawingUtilityToolbar` at the top of the screen (below the header).
    *   Move Undo, Redo, and Paste logic from `DrawingToolbar` / `ToolbarContent` to this new component.
    *   Remove these actions from the main `DrawingToolbar` at the bottom.

4.  **Z-Index and Layout:**
    *   Ensure the new `DrawingUtilityToolbar` has a high `zIndex` to appear above the canvas.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
1.  **General Drawing:** Verify drawing still works correctly with pen, eraser, and lasso.
2.  **Tablet Mode:**
    *   Open drawing in split-screen on a tablet (or emulator).
    *   Verify the 28.dp rounded corner at the top-start.
    *   Verify drawing is clipped at the top (doesn't bleed into status bar).
3.  **Toolbar:**
    *   Verify Undo/Redo/Paste now appear at the top utility bar.
    *   Verify they are removed from the bottom toolbar.
4.  **Status Bar:**
    *   Verify icons are light (white) on the drawing screen.
