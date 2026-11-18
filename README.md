# Skylume

**Skylume** is an atmospheric Android weather visualizer built with Kotlin and Jetpack Compose. For each city, it pairs live meteorological telemetry and a real-time particle canvas with a **Gemini-generated 3D miniature horizontal cluster of iconic landmarks and buildings** rendered dynamically to match the location, weather condition, and time of day.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [App Configuration](#app-configuration)
  - [Firebase AI Logic Setup](#firebase-ai-logic-setup)
- [Build & Test](#build--test)
- [License](#license)

---

## Features

- **Gemini 3D Miniature Skyline Backdrops**
  - Uses **Firebase AI Logic (`gemini-nano-banana-2.1`)** to generate a 16:9 stylized 3D miniature horizontal cluster of local landmarks and characteristic buildings tailored to the active city, current weather, and day/night lighting.
  - Caches generated dioramas in local app cache by location, weather condition, and day/night phase, with horizontal drag-to-pan and an on-demand **Regenerate** button.
  - Protected with **Firebase App Check** (`DebugAppCheckProviderFactory` in debug builds, `RecaptchaAppCheckProviderFactory` in release builds).

- **Live Atmospheric Particle Visualizer**
  - 60fps Jetpack Compose `Canvas` (`WeatherParticlesCanvas`) synchronized to live weather conditions, wind speed, cloud cover, and day/night state:
    - Clear day sunbeams & warm floating dust motes
    - Clear/partly cloudy night twinkling starfield
    - Drifting clouds and low-altitude fog bands
    - Wind-slanted rain/drizzle streaks and thunderstorm lightning flashes
    - Gently swaying snowfall crystals

- **Live Weather Telemetry & Forecasts**
  - **24-Hour Horizon**: Interactive hourly forecast cards paired with a smooth cubic Bezier temperature spline and precipitation probability indicators.
  - **Atmospheric Telemetry**: Wind speed with directional compass dial, humidity, sea-level pressure, UV peak rating, cloud cover, and sunrise/sunset daylight summary.
  - **7-Day Outlook**: Daily min–max thermal range bars across a temperature gradient.

- **Resilient Sideload-Ready Networking**
  - Primary weather and geocoding via **Open-Meteo** (`api.open-meteo.com`, `geocoding-api.open-meteo.com`).
  - **3-Tier `ResilientDns` Resolver**: Falls back automatically from IPv4-prioritized System DNS to **DNS-over-HTTPS** (`1.1.1.1`, `1.0.0.1`, `8.8.8.8`, `8.8.4.4`) and **direct UDP port-53** RFC 1035 queries, plus `ConnectivityManager` diagnostics.
  - **Secondary Provider Fallback**: Automatically switches to **MET Norway (`api.met.no`)** and **OpenStreetMap Nominatim** if Open-Meteo is unreachable.

- **Location Discovery & Adaptive UI**
  - Worldwide city search, curated iconic skylines (`Tokyo`, `Paris`, `New York`, `London`, `Reykjavik`, `Cairo`, `Sydney`), bookmarked locations & `°C`/`°F` preference persisted via **DataStore Preferences**, and optional **coarse GPS location** (`ACCESS_COARSE_LOCATION`).
  - **Adaptive Layouts**: Single-column hero + scrollable glass telemetry with **Immersive Diorama Mode** on phones, and a two-pane studio layout on `>= 840.dp` tablets/foldables.

---

## Tech Stack

- **UI**: Kotlin, Jetpack Compose, Material 3, edge-to-edge `WindowInsets`, custom bundled Google Fonts (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`)
- **AI**: Firebase AI Logic (`com.google.firebase:firebase-ai`) with `gemini-nano-banana-2.1` & Firebase App Check
- **Networking**: Retrofit 2, OkHttp 4 (`ResilientDns`), Moshi (`ksp` codegen)
- **Persistence & Location**: Jetpack DataStore Preferences, Google Play Services Location (`PRIORITY_BALANCED_POWER_ACCURACY`), Android `Geocoder`
- **Testing**: JUnit 4, Robolectric, Coroutines Test

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                          # App Check init, edge-to-edge setup, theme host
├── firebase/
│   └── AppCheckInitializer.kt               # Debug/release App Check provider setup
├── data/
│   ├── ai/DioramaGenerator.kt               # Gemini 3D miniature skyline generation & disk cache
│   ├── local/PreferencesRepository.kt       # DataStore persistence (saved cities, °C/°F)
│   ├── location/CoarseLocationClient.kt     # Coarse GPS + Geocoder reverse lookup
│   ├── model/WeatherModels.kt               # Domain models, WMO mapper, sky palettes
│   └── remote/
│       ├── OpenMeteoApi.kt                  # Open-Meteo Retrofit client & WeatherRepository
│       ├── MetNorwayFallbackApi.kt          # MET Norway & Nominatim fallback client
│       └── ResilientDns.kt                  # System DNS + DoH + UDP:53 resolver
└── ui/
    ├── WeatherUiState.kt                    # Sealed UI states for weather, diorama, and search
    ├── WeatherViewModel.kt                  # MVVM state orchestration
    ├── WeatherVisualizerScreen.kt           # Adaptive Compact/Expanded screen & immersive mode
    ├── components/
    │   ├── DioramaBackdropStage.kt          # Gemini skyline backdrop, parallax, shimmer, retry
    │   ├── WeatherParticlesCanvas.kt        # 60fps atmospheric weather particle Canvas
    │   ├── WeatherMetricsSection.kt         # 24h spline, wind compass, telemetry, 7-day bars
    │   ├── LocationSearchSheet.kt           # City search, curated skylines, saved list
    │   └── WeatherIcons.kt                  # Condition-to-icon and tint mappings
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## App Configuration

- **Version**: `1.0` (`versionCode = 1`)
- **Application ID**: `com.aistudio.skylume.vqtxkp`
- **SDK**: `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36.1`
- **Permissions**: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`

### Firebase AI Logic Setup

Skylume routes Gemini image generation through **Firebase AI Logic** (`Firebase.ai`), so no client-side `GEMINI_API_KEY` is used:
- Ensure `app/google-services.json` is configured for your Firebase project with AI Logic enabled (requires the Blaze plan for image generation models).
- For release builds, `app/src/main/res/values/firebase_applet_config.xml` supplies `recaptcha_site_key` for App Check attestation.

---

## Build & Test

Run unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

Build the debug APK:

```bash
gradle :app:assembleDebug
```

---

## License

Skylume is licensed under the MIT License (`SPDX-License-Identifier: MIT`).

See [`LICENSE.txt`](LICENSE.txt) for full terms and conditions.
