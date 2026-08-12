# Notes

A modern, feature-rich Android note-taking application built with Jetpack Compose, focusing on versatility, adaptive layouts, and advanced stylus support.

## 🚀 Features

### 🖋️ Advanced Drawing
- **Precision Canvas**: Near-zero latency drawing optimized for styluses.
- **S Pen Integration**: Full support for S Pen side-button (hold to erase) and dedicated eraser tips.
- **Object-Based Erasing**: Erase entire strokes with ease.
- **Variable Tools**: Pen (with thickness/color control), Eraser, Lasso selection, and Hand/Pan tool.
- **Color Selection**: Preset colors and a full HSV custom color picker.

### 📝 Versatile Note Types
- **Rich Text Notes**: Support for bold, italic, underline, bullet points, and dynamic headings.
- **Checklists**: Nested task management with customizable behaviors (hide completed, move to bottom, etc.).
- **Rating Lists**: Rate items (e.g., movies, books) on a 0-10 scale with visual indicators.

### 📱 Adaptive & Modern UI
- **Split-Screen Layout**: Custom resizable dual-pane interface for tablets and foldables.
- **Material 3**: Full implementation of Material You with dynamic color support.
- **Theming**: Seamless switching between Light, Dark, and System themes.
- **Typography**: Uses **Google Sans Flex** variable font with custom "Roundness" axes for a modern look.

### 🛠️ Technical Highlights
- **Architecture**: Clean MVVM (Model-View-ViewModel) with Repository pattern.
- **Persistence**: Powered by **Room** for a fully offline experience.
- **Exporting**: Export drawings as high-quality PNGs or PDFs (Vector and Bitmap).
- **Reactive UI**: State-driven UI updates using Kotlin Coroutines and Flow.

## 🛠️ Build Requirements
- Android Studio Ladybug (or newer)
- Android SDK 30+ (Min SDK) / 36 (Target SDK)
- Gradle 8.0+

## 📄 License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.
