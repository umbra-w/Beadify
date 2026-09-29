# Beadify - Fuse Bead Pattern Generator (Android)

A native Android studio for fuse bead crafting and pixel art design, built with Kotlin and Jetpack Compose.

[English](./README_EN.md) · [简体中文](./README.md) · [Test Specification](./docs/TEST_SPECIFICATION.md) · [Full Test Report](./docs/TEST_REPORT.md) · [Algorithm Benchmark Report](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## About Beadify

Beadify is a native Android application designed for fuse bead makers (Perler, Hama, Artkal, Mard) and pixel art enthusiasts.

Traditional web-based pattern tools often struggle with browser memory limits, a lack of pixel fonts, color mapping inaccuracies, and distorted print sizes. Beadify combines hardware-accelerated Android architecture with the Oklab perceptually uniform color space to deliver a seamless workflow: from image/text input to intelligent color quantization, outline/shadow styling, board slicing, and 1:1 true-scale physical PDF printing.

---

## Key Features

### 1. Image Pixelation and Quantization Engine
- **Dual Conversion Modes**:
  - **Cartoon Mode (Dominant Color)**: Local histogram analysis extracts dominant colors for crisp outlines, ideal for anime and illustrations.
  - **Realistic Mode (Average Color)**: Weighted spatial sampling ensures smooth, continuous gradients.
- **Smart Cropping with Ratio Presets**: Freeform crop alongside 1:1 (square), 1:2 (vertical), and 2:1 (horizontal) presets with aspect-ratio locking for common board layouts (e.g. 28×28 standard pegboards).
- **Automated Despeckling**: 8-neighborhood connected component analysis detects and removes 1–2 bead isolated stray artifacts, drastically improving physical assembly practicality.
- **Color Tuning and Optimization**:
  - Floyd-Steinberg error-diffusion dithering for smooth color transitions.
  - Color similarity merging threshold to consolidate adjacent color tones and minimize stray bead variety.

### 2. Text Beads Workshop
- **Custom Typography and Heights**: Type any alphanumeric or CJK text, selecting from 24, 32, 50, or 72 grid row heights.
- **Built-in Open-Source Pixel Fonts**: Ships with 3 classic pixel fonts under the SIL OFL 1.1 license, alongside the system default font:
  - **Ark Pixel 12px** (TakWolf): Classic Chinese retro dot-matrix style.
  - **Fusion Pixel 12px** (TakWolf): Comprehensive CJK glyph coverage.
  - **Press Start 2P** (Google Fonts): Iconic 8px arcade-style Latin font.
- **Color Modes**:
  - **Single Color**: Quick uniform color text generation.
  - **Gradient**: Oklab color-space linear interpolation between start and end colors, automatically snapped to the closest physical bead palette entries.
  - **Rainbow**: Automatic horizontal cycling across the hue wheel.
- **Text Layer Effects**:
  - **Outline**: 8-neighborhood morphological dilation creates clean border outlines in any selected bead color.
  - **Drop Shadow**: Independent coordinate offset layer with automated overlap subtraction.
  - Layer composition: Text > Outline > Shadow > Background.
- **Background Options**: Toggle between solid white fill and transparent background (text only).

### 3. Canvas Editing and Multi-Board Slicing
- **Editor Toolbox**:
  - Hand-drawing pencil and eraser.
  - One-tap isolated speckle cleanup.
  - Multi-touch viewport zooming, panning, and center reset.
- **Board Work Mode**:
  - Quick toggle between 28×28 (standard board) and 16×16 (mini board) specifications.
  - Crosshair ruler coordinates indicating active sub-board row and column indices.
  - Checkmark tracking per bead cell to prevent misalignment during hands-on assembly.
- **Local Project Drafts**: Multi-slot project saving, thumbnail preview list, editing restoration, and deletion.

### 4. Palette System and Inventory Tracking
- **Standardized Color Database**: Built-in 290+ color dataset covering major international and domestic brands:
  - Artkal S (Soft beads, 235 colors), Artkal C (Hard beads, 174 colors).
  - Perler (Classic American standard).
  - MARD, COCO, ManMan, PanPan, MiXiaoWo, and more.
- **Personal Bead Inventory**: Mark owned stock to trigger missing bead alerts with red highlight badges on the canvas and color breakdown tables.

### 5. 1:1 Scale Output and Multi-Format Export
- **Vector High-Res PDF Printing**:
  - 300 DPI vector rendering ensures crystal-clear bead codes and grid lines.
  - Exact 1:1 true-scale printing for **2.6mm (mini)** and **5.0mm (standard)** physical bead pitches, designed for direct placement beneath transparent pegboards.
- **Grid Accent Lines**: Customizable intervals (every 5 or 10 beads) with selectable guide line colors.
- **Comprehensive File Formats**:
  - High-resolution PNG pattern sheets with coordinate rulers and bead key codes.
  - Responsive multi-column color usage tables.
  - Shopping list CSV (color keys, HEX codes, quantities).
  - Grid pattern CSV for cross-platform data interchange.

---

## Architecture and Tech Stack

The project adheres to Clean Architecture and MVI/MVVM unidirectional state flow:

```
perler-beads-android/
├── app/
│   ├── src/main/
│   │   ├── assets/fonts/       # Bundled open-source pixel fonts (Ark Pixel, Fusion Pixel, Press Start 2P)
│   │   ├── java/com/perlerbeads/generator/
│   │   │   ├── algorithm/     # Core algorithms (Oklab, quantization, morphology, dithering, despeckle)
│   │   │   ├── data/          # Data layer (palette dataset, inventory storage, project persistence)
│   │   │   ├── export/        # Export engine (300 DPI vector PDF, CSV and PNG rendering)
│   │   │   ├── model/         # Domain models and immutable state
│   │   │   ├── navigation/    # Compose Navigation routing
│   │   │   └── ui/            # UI components (Material 3, Home, Crop, Text Beads, Editor, Palette)
│   └── src/test/              # JVM white-box unit test suite (150 tests)
├── docs/                      # Test specifications, execution reports, and benchmarks
├── release/                   # Release documentation and signed APK binaries
└── scripts/                   # Real-device ADB E2E automation test suite
```

- **Core Frameworks**: Kotlin 2.0+, Jetpack Compose, Material Design 3
- **Reactive Concurrency**: Kotlin Coroutines, StateFlow
- **Platform Integration**: Edge-to-Edge display support, AndroidX Photo Picker

---

## Quality Assurance and Testing

### 1. JVM White-Box Unit Testing
Validates Oklab $\leftrightarrow$ sRGB roundtrip precision, TextBeads gradient interpolation, morphological dilation bounds, PDF vector layouts, and slicing algorithms:
- **Test Count**: 150 automated test cases (100% PASS, 0 failures).
- **Execution Command**: `./gradlew testReleaseUnitTest --offline`

### 2. Physical Device ADB E2E Automation
Built according to the `android-testing-skills` specification, executed end-to-end on a physical Xiaomi Redmi 13C (Android 14):
- **Suite 1**: Full Image Pipeline (Photo Picker $\rightarrow$ Crop Ratios 1:1/1:2/2:1 $\rightarrow$ Quantization Settings $\rightarrow$ 50×50 Pattern Generation).
- **Suite 2**: Canvas Tools (Pencil drawing, Eraser, Speckle cleanup, Viewport reset).
- **Suite 3**: Board Slicing (16×16 / 28×28 toggles, Checkmark tracking).
- **Suite 4**: Project Persistence (Save project, List preview, Restore editing, Delete project).
- **Suite 5**: Text Beads (Custom input, Pixel font selection, Gradient color mode, Outline effect, Canvas entry).
- **Suite 6**: Palette Management (Brand switching, Inventory tracking, Atomic save).
- **Suite 7**: Export Validation (300 DPI PDF, Pattern and Shopping List CSVs, Key-coded PNG).
- **Phase 8**: System Stability & Energy Audit (0 Logcat Crashes, 0 ANRs, Normal memory usage).

---

## Getting Started

### Prerequisites
- JDK 17 or higher
- Android SDK (Compile SDK 35 / Min SDK 24 / Target SDK 35)
- Gradle 8.13+

### Build and Run

```bash
# Clone the repository
git clone https://github.com/umbra-w/Beadify.git
cd Beadify

# Run unit tests
./gradlew testReleaseUnitTest

# Build Release APK
./gradlew assembleRelease

# Run physical ADB E2E automation tests (requires connected Android device with USB debugging enabled)
python scripts/run_full_feature_e2e.py
```

---

## References & Acknowledgements

Beadify draws significant inspiration and reference from the vibrant fuse bead and pixel art open-source communities. We gratefully acknowledge the following projects and contributors:

### Fuse Bead Open-Source Ecosystem
- **[perler-beads](https://github.com/zippland/perler-beads)** (zippland) & **[PindouAI / perler-beads-ai](https://github.com/xuange6610/PindouAI)** (xuange6610): Innovative web-based bead generator prototypes and exploration projects that inspired our initial dual-mode quantization concepts and core workflow.
- **[QiaoGrid](https://github.com/xiaoxuesheng123467/QiaoGrid)** (xiaoxuesheng123467): An exceptional mobile fuse bead tool that provided invaluable design inspiration and practical experience for palette presentation, spotlight assembly assistance, and edge background removal.
- **[BeadPalette](https://github.com/liberatrrot/BeadPalette)** (liberatrrot): Color palette exploration tool for multi-brand color cross-referencing and inventory organization.
- **[beadcolors](https://github.com/maxcleme/beadcolors)** (maxcleme): Comprehensive international fuse bead brand color codes, names, and standardized HEX datasets.
- **[pindou-format-tool](https://github.com/GarrusHuang/pindou-format-tool)** (GarrusHuang): Domestic fuse bead palette and pattern format interchange utility.

### Open-Source Typography
- **[Ark Pixel Font](https://github.com/TakWolf/ark-pixel-font)**: Created by TakWolf, licensed under the SIL Open Font License 1.1.
- **[Fusion Pixel Font](https://github.com/TakWolf/fusion-pixel-font)**: Created by TakWolf, comprehensive CJK character set, licensed under the SIL Open Font License 1.1.
- **[Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P)**: Created by CodeMan38, hosted on Google Fonts, licensed under the SIL Open Font License 1.1.

### Algorithms and Standards
- **[Oklab](https://bottosson.github.io/posts/oklab/)**: Perceptually uniform color space model and color difference formulas by Björn Ottosson, utilized for perceptual color interpolation and nearest-neighbor physical palette matching.

---

## License

This project is open-source under the [MIT License](LICENSE).
