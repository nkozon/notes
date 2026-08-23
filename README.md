# Notes

A modern, feature-rich Android note-taking application built with Jetpack Compose, focusing on versatility, adaptive layouts, and advanced stylus support.

## Features

### Advanced Drawing
- **Stylus Integration**: Full support for S Pen (and other passive styluses) side-button (hold to erase) and dedicated eraser tips.
- **Variable Tools**: Pen (with thickness/color control), Eraser, Lasso selection, and Hand/Pan tool.
- **Color Selection**: Preset colors and a full HSV custom color picker. Also change color of lasso-selected strokes.

### Versatile Note Types
- **Rich Text Notes**: Support for bold, italic, underline, bullet points, and dynamic headings.
- **Checklists**: Nested task management with customizable behaviors (hide completed, sink, etc.).
- **Rating Lists**: Rate items (e.g. movies, books) on a 0-10 scale with visual indicators. Also uses TMDb as the poster fetcher.
- **Upcoming Lists**: Track upcoming items (e.g. upcoming movies) and get notified when the time comes.

### Adaptive & Modern UI
- **Dual-pane Layout**: Custom resizable dual-pane interface for tablets and foldables.
- **Material 3 & Themes**: Full implementation of Material You with dynamic color support, custom theming and the Google Sans Flex font.

### Technical Highlights
- **Architecture**: Clean MVVM (Model-View-ViewModel) with Repository pattern.
- **Persistence**: Powered by **Room** for a fully offline experience.
- **Exporting**: Export drawings as high-quality PNGs or PDFs (Vector and Bitmap).
- **Reactive UI**: State-driven UI updates using Kotlin Coroutines and Flow.

## Attributions
- **TMDb**: This product uses the TMDB API but is not endorsed or certified by TMDB. [themoviedb.org](https://www.themoviedb.org)

  <img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg" width="100" alt="TMDb Logo">

## Build Requirements
- Android Studio Ladybug (or newer)
- Android SDK 30+ (Min SDK) / 36 (Target SDK)
- Gradle 8.0+

## License
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

## One more thing
If it wasn't obvious, this project is mostly written by AI, with me steering it into developing this project.
I will rewrite parts of the code that aren't up to (my) standards when the time comes.
