# Beadify - Fuse Bead Pattern Generator (Android)

A native Android studio for fuse bead crafting and pixel art design, built with Kotlin and Jetpack Compose.

[English](./README_EN.md) · [简体中文](./README.md) · [Test Specification](./docs/TEST_SPECIFICATION.md) · [Full Test Report](./docs/TEST_REPORT.md) · [Algorithm Benchmark Report](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## About Beadify

Beadify is a native Android application designed for fuse bead makers (Perler, Hama, Artkal, Nabbi) and pixel art enthusiasts.

Traditional web-based pattern tools often suffer from browser memory limits, performance stutter on large photos, rigid palette matching, and awkward print layouts. Beadify uses hardware-accelerated Kotlin algorithms to deliver 0.71-second instant conversion for 12MP photos, seamless remapping across 7 major brand palettes, personal bead inventory tracking with Oklab-based color substitution, 1:1 true-scale printing, and multi-board slicing with spotlight assistance.

---

## Key Features

### 1. High-Performance Pixelation Engine
- Dual Quantization Modes:
  - Cartoon Mode (Dominant Color): Extracts local histogram dominant colors for crisp outlines, ideal for anime and illustrations.
  - Realistic Mode (Average Color): Weighted spatial sampling for smooth photographic transitions.
- Island Speckle Cleanup (Despeckle): 8-neighborhood connected component analysis detects and cleans 1-2 bead isolated artifacts, significantly improving physical assembly practicality.
- Floyd-Steinberg Dithering: Smooth color gradients across large areas.
- Speed and Memory Protection: Generates patterns from 12-megapixel (4000x3000) photos in 0.71 seconds with progressive sub-sampling to prevent OOM.

### 2. Multi-Brand Authority Palette System
- Built-in standardized 291-color database.
- Real-time lossless remapping across 7 popular brands:
  - Artkal S (Soft beads): 235-color precision benchmark.
  - Perler: Classic American bead standard.
  - Domestic Standards: MARD, COCO, ManMan, PanPan, MiXiaoWo.
- CIEDE2000 and weighted perceptual color distance (Delta E < 2.5).

### 3. Inventory Management and Oklab Smart Substitution
- Local Inventory Tracking: Toggle stock status per color code with fuzzy search.
- Missing Bead Visual Alerts: Red dot badges on the editor canvas and highlight warnings in the color statistics table.
- Smart Substitution: Calculates the closest match among currently stocked beads using perceptual color distance, ranked with 5-star ratings, enabling one-tap batch replacement.

### 4. Board Slicing and Spotlight Mode
- Multi-Board Slicing: Automatically slices oversized patterns into standard pegboard layouts (e.g. 29x29, 50x50) with sub-board coordinates (A1, A2, etc.).
- Spotlight Mode: Highlights the active board or a specific color while dimming non-target areas, reducing eye strain during assembly.

### 5. 1:1 Physical Export and Adaptive Layout
- 1:1 Scale Output: Supports 2.6mm (mini) and 5.0mm (midi) standard hole pitches for direct overlay under transparent pegboards.
- Adaptive Multi-Column Statistics: Automatically formats 1 to 6 columns based on pattern width, eliminating excessively elongated vertical tables.
- Multi-Format Export: High-resolution PNG pattern (with coordinates and codes), color usage PNG, and Excel-compatible UTF-8 BOM CSV shopping lists.

### 6. Manual Pixel Editing and Typography
- Full Toolbox: Pencil, Eraser, Flood Fill, Color Swap, Background Removal, and Color Exclusion.
- History: Multi-step Undo and Redo stack.
- Typography: Generates nameplate bead patterns from custom text input (alphanumeric and CJK).
- Local Projects: Save, load, and rename ongoing bead projects.

---

## Architecture and Tech Stack

The project follows Clean Architecture with MVI/MVVM:

```
perler-beads-android/
├── app/
│   ├── src/main/java/com/perlerbeads/generator/
│   │   ├── algorithm/     # Core algorithms (pixelation, color math, despeckle, substitution, dithering, slicing)
│   │   ├── data/          # Data layer (palette assets, inventory storage, project serialization)
│   │   ├── export/        # Export engine (1:1 drawing, adaptive layout, CSV encoding)
│   │   ├── model/         # Data models and immutable states
│   │   ├── navigation/    # Compose Navigation routes
│   │   └── ui/            # UI layer (Material 3, Home, Crop, Editor, Palette, Inventory, Settings)
│   └── src/test/          # Automated white-box unit tests (110 tests)
├── docs/                  # Test specifications, test reports, and benchmarks
└── scripts/               # Real-device ADB E2E automation and benchmark scripts
```

Tech Stack: Kotlin 2.0+, Jetpack Compose (Material Design 3), Kotlin Coroutines + StateFlow, AndroidX Photo Picker, JUnit 4 / Robolectric / ADB Shell.

---

## Performance Benchmarks

Measured on a physical Xiaomi Redmi 13C (MediaTek Helio G85, Android 14):

| Benchmark Scenario | Beadify Native | Web-based Baseline |
| :--- | :--- | :--- |
| 12MP Photo Generation | **0.71 s** | ~4.2 s (5.9x faster) |
| Peak Memory Usage | **56 MB** | >380 MB (85% reduction) |
| Isolated Artifact Elimination | **98.4%** | Numerous stray beads |
| Palette Matching Precision | **CIEDE2000 (Delta E <= 2.1)** | Euclidean (Delta E ~ 4.8) |

Detailed benchmark methodology is documented in [Algorithm Benchmark Report](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md).

---

## Quality Assurance and Testing

### Testing Framework
- Static Analysis: Android Lint 0 Errors, 0 Warnings, zero memory leaks.
- White-Box Unit Testing: 21 core test classes, 110 automated test cases (100% PASS).
- Physical ADB E2E Testing: 31 fine-grained user operations verified on connected physical hardware.
- Algorithm Evaluation: Quantitative validation across 4 typical test scenarios.

### Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Physical ADB E2E automation (requires Android device with USB debugging enabled)
powershell -ExecutionPolicy Bypass -File scripts/run_full_feature_e2e_tests.ps1
```

See [Test Specification](./docs/TEST_SPECIFICATION.md) and [Full Test Report](./docs/TEST_REPORT.md).

---

## Getting Started

### Prerequisites
- JDK 17 or higher
- Android SDK (Compile SDK 35 / Min SDK 24)
- Gradle 8.7+

### Build and Install

```bash
# Clone the repository
git clone https://github.com/umbra-w/Beadify.git
cd Beadify

# Build Debug APK
./gradlew assembleDebug

# Output artifact: app/build/outputs/apk/debug/app-debug.apk

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch application
adb shell am start -n com.perlerbeads.generator/.MainActivity
```

---

## Documentation Links

- [Test Specification](./docs/TEST_SPECIFICATION.md)
- [Full Test Execution Report](./docs/TEST_REPORT.md)
- [Algorithm Benchmark Report](./docs/benchmarks/ALGORITHM_BENCHMARK_REPORT.md)

---

## References

This project references and builds upon the following projects and standards:

- **perler-beads / perler-beads-ai** - Web prototype inspiring the initial dual-mode pixelation approach and workflow concepts.
- [beadcolors](https://github.com/maxcleme/beadcolors) - International fuse bead brand color codes and HEX reference dataset.
- [pindou-format-tool](https://github.com/GarrusHuang/pindou-format-tool) - Domestic bead palette mappings and format conventions.
- [Oklab](https://bottosson.github.io/posts/oklab/) - Perceptually uniform color space model.

---

## License

This project is licensed under the [MIT License](LICENSE).
