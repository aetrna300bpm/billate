# Billate (MVP) — Build & Dependency Notes

This document summarizes the **dependencies**, **compatible versions**, and **Gradle requirements** to build the Billate-style app described in the design docs in this repo. It uses the existing template structure and versions from the project catalog.

## 1) Compatible Versions (from Version Catalog)
These versions are already defined in the project’s version catalog. Use them as-is for compatibility.

- **Android Gradle Plugin (AGP):** 8.8.2
- **Kotlin:** 2.1.0
- **KSP:** 2.1.0-1.0.29
- **Compose BOM:** 2025.06.01
- **Firebase BOM:** 34.5.0
- **Hilt:** 2.56.2
- **Kotlinx Serialization JSON:** 1.6.2
- **Core KTX:** 1.15.0
- **Activity Compose:** 1.10.1
- **Navigation Compose:** 2.9.0
- **Lifecycle Runtime (KTX):** 2.8.7
- **Lifecycle Runtime Compose:** 2.9.1
- **Material3:** 1.5.0-alpha01

Full list in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## 2) Required Gradle Plugins (App Module)
Use these plugins in the app module (see [app/build.gradle.kts](app/build.gradle.kts)):

- `com.android.application`
- `org.jetbrains.kotlin.android`
- `org.jetbrains.kotlin.plugin.serialization` (for strict JSON parsing)
- `com.google.gms.google-services` (Firebase)
- `com.google.dagger.hilt.android` (DI)
- `com.google.devtools.ksp` (annotation processing)
- `org.jetbrains.kotlin.plugin.compose` (Compose compiler)

## 3) Core Dependencies and Usage
Below is the Billate MVP dependency set mapped to their usage.

### UI & Navigation
- **Jetpack Compose UI** (Compose BOM)
  - UI layer, screen layout, Material3 components.
  - Dependencies: `androidx-ui`, `androidx-ui-graphics`, `androidx-ui-tooling-preview`, `androidx-material3`.
- **Navigation Compose**
  - Screen routing between Home and Review.
  - Dependency: `androidx-navigation-compose`.

### Architecture & State
- **Lifecycle Runtime (KTX/Compose)**
  - Lifecycle-aware state and ViewModel integration.
  - Dependencies: `androidx-lifecycle-runtime-ktx`, `androidx-lifecycle-runtime-compose`.

### Dependency Injection
- **Hilt**
  - Inject repository, data sources, use cases, and ViewModels.
  - Dependencies: `hilt-android`, `hilt-navigation-compose`, `hilt-compiler` (KSP).

### AI / Cloud (Gemini via Firebase)
- **Firebase AI SDK** (Gemini access)
  - Used for single-call strict JSON extraction as defined in docs.
  - Dependencies: `firebase-bom`, `firebase-ai`.
  - Requires `google-services.json` in the app module.

### JSON Parsing
- **Kotlinx Serialization**
  - Strict JSON parsing of Gemini responses.
  - Dependency: `kotlinx-serialization-json`.

### Optional (if needed in Billate)
- **Coil** for image loading in Compose (if you show receipt images in review).
  - Dependency: `coil-compose`.

## 4) Android Build Configuration (Recommended)
Based on current templates:

- **compileSdk:** 36
- **targetSdk:** 36
- **minSdk:** 26 (Billate uses image picker and modern APIs)
- **Java/Kotlin target:** 17
- **Compose enabled**: `buildFeatures { compose = true }`

The base settings are already in [app/build.gradle.kts](app/build.gradle.kts).

## 5) Firebase Setup (Required for Gemini)
The Gemini Firebase SDK requires a Firebase project and a config file:

1. Create a Firebase project.
2. Add Android app with the correct `applicationId`.
3. Download `google-services.json` and place it in the app module folder.
4. Sync Gradle.

See README guidance in [README.md](README.md).

## 6) Minimal App Module Example (Gradle)
Use the existing app module and keep only the dependencies required for Billate.

Required dependencies (subset):
- Compose BOM + Material3 + Activity Compose
- Navigation Compose
- Lifecycle runtime (KTX/Compose)
- Hilt + KSP
- Firebase AI (Gemini) via Firebase BOM
- Kotlinx Serialization JSON

## 7) How to Build (CLI)
From the repo root:

- **Debug build:** `./gradlew :app:assembleDebug`
- **Install on device:** `./gradlew :app:installDebug`

If you are using Android Studio, just **Sync** and **Run** the app configuration.

## 8) Notes for Billate Feature Set
To match the Billate MVP documents:

- The **Gemini call** should be single-shot with **strict JSON** output.
- Validation rules and VND normalization are in [docs/prompt-spec.md](docs/prompt-spec.md).
- Data persistence should use **Room** (add dependencies if not already present in your module).

If you add Room to the Billate module, include:
- `androidx.room:room-runtime`
- `androidx.room:room-ktx`
- `androidx.room:room-compiler` (KSP)

You can add these to the version catalog and then use them via `libs.` references.
