# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview
Aprecio is an Android application that analyzes grocery store product images to detect deceptive pricing practices. The app uses CameraX for image capture and ML Kit for text recognition to extract product information from shelf labels.

## Development Commands

### Build
```bash
./gradlew build
```

### Run Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires Android device/emulator)
./gradlew connectedAndroidTest
```

### Clean Build
```bash
./gradlew clean
```

### Install Debug APK
```bash
./gradlew installDebug
```

## Architecture

### Core Components
- **MainActivity**: Main UI controller handling camera, gallery, and analysis flow
- **PriceAnalyzer**: ML Kit-based text recognition and price extraction logic
- **OverlayManager**: Manages visual overlays on detected products in images
- **ProductsAdapter**: RecyclerView adapter for displaying analyzed products

### Key Data Models
- **ProductInfo**: Represents a detected product with pricing, weight, and deception analysis
- **PriceAnalysisResult**: Container for multiple detected products

### Technology Stack
- **Android SDK**: Target SDK 35, Min SDK 24
- **Kotlin**: Primary language
- **CameraX**: Camera functionality (core, camera2, lifecycle, view)
- **ML Kit**: Text recognition for price extraction
- **View Binding**: UI binding approach

### Image Processing Flow
1. Capture image via CameraX or select from gallery
2. Convert to InputImage for ML Kit processing
3. Extract text using TextRecognition
4. Parse text blocks for product information (names, prices, weights)
5. Calculate price-per-kg and detect deceptive pricing patterns
6. Display results with clickable overlays and RecyclerView list

### Deceptive Pricing Detection
The app analyzes:
- Weight discrepancies (products sold by weight vs. piece)
- Multiple pricing schemes on same label
- Suspicious weight amounts (0.25kg, 0.5kg, etc.)
- Price-per-kg calculations vs. displayed prices

## Project Structure
```
app/src/main/
├── java/com/danifgx/aprecio/
│   ├── MainActivity.kt          # Main activity and camera handling
│   ├── PriceAnalyzer.kt        # ML Kit text recognition and price logic
│   ├── OverlayManager.kt       # Image overlay management
│   └── ProductsAdapter.kt      # RecyclerView adapter
├── res/
│   ├── layout/                 # XML layouts
│   └── drawable/               # Image resources
└── AndroidManifest.xml
```

## Development Notes
- Uses View Binding for UI components
- Camera permissions required for image capture functionality
- ML Kit text recognition processes images asynchronously
- Product detection uses regex patterns for Spanish grocery labels
- Overlay positioning scales based on ML Kit bounding boxes