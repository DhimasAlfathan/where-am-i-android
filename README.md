<p align="center">
  <img src="app/src/main/res/drawable-nodpi/launcher_art.png" width="150" alt="Where am I app icon" />
</p>

<h1 align="center">Where am I ??</h1>
<p align="center">An Android location-testing app with searchable maps, recent places, and personalized favorites.</p>
<p align="center"><strong>Kotlin · Android SDK · Leaflet · OpenStreetMap · JUnit</strong></p>

## Overview

Where am I ?? is a personal Android development project that makes it easier to select and reuse locations during testing. Search for an address or enter coordinates, inspect the point on a map, and start or stop Android's mock location provider.

Version **1.5** adds persistent location history and favorites with custom names. The interface is in English and the app runs on **Android 8.0 (API 26) or later**.

## Features

| Feature | Behavior |
| --- | --- |
| Address search | Uses the device's Android Geocoder, with a timeout and clear failure messages. |
| Coordinate input | Accepts `latitude, longitude` and validates coordinate ranges. |
| Map preview | Leaflet in a WebView with OpenStreetMap tiles, a location pin, and retry handling. |
| Recent places | Opens when the search field is tapped; retains up to 30 selected locations without duplicates. |
| Favorites | Save or remove a location using its star button. |
| Custom names | Rename favorites without changing their original address or coordinates. |
| Persistent storage | History and favorites survive app restarts; clearing history keeps favorites. |
| Mock location controls | A foreground service updates test providers and restores them when stopped. |

## What this project demonstrates

- **Android service lifecycle:** foreground notifications, explicit start/stop controls, and provider cleanup.
- **Asynchronous UI work:** geocoder callbacks, background execution on older Android versions, timeouts, and stale-result cancellation.
- **Data modeling and persistence:** coordinate-based identity, bounded recent history, custom favorite labels, and JSON storage in SharedPreferences.
- **Native/web integration:** Android Views alongside a restricted WebView loading bundled Leaflet assets.
- **Testing:** six JVM tests for saved-place behavior and a JavaScript test exercising real Leaflet in a simulated DOM.

## Architecture

```text
MainActivity
  ├── Android Geocoder → address search results
  ├── SavedPlaces → JSON → SharedPreferences
  ├── WebView → bundled Leaflet → OpenStreetMap tiles
  └── MockLocationService → Android test location providers
```

| File | Responsibility |
| --- | --- |
| [MainActivity.kt](app/src/main/java/com/example/mocklocationtester/MainActivity.kt) | Search, selection, history/favorite controls, and map integration. |
| [SavedPlaces.kt](app/src/main/java/com/example/mocklocationtester/SavedPlaces.kt) | Deduplication, the 30-item history limit, favorites, renaming, and serialization. |
| [MockLocationService.kt](app/src/main/java/com/example/mocklocationtester/MockLocationService.kt) | Mock provider updates, foreground notification, and cleanup. |
| [map.html](app/src/main/assets/map.html) | Map presentation, pin updates, and tile-error recovery. |

## Build locally

Requirements: JDK 17, Android SDK platform 35, and Android SDK Build Tools 35.0.0. Android Studio can install the SDK components and open the project.

1. Clone this repository.
2. Set `ANDROID_HOME`, or create a local `local.properties` file containing `sdk.dir=/your/android/sdk/path`.
3. Build the debug app:

```sh
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Windows users can run `gradlew.bat assembleDebug`.

**Signing:** this public repository uses Android's default local debug signing setup. No private signing keys are included. A build on another machine may not install over an existing copy signed with a different key. Uninstalling an existing copy clears its saved data.

## Try it on Android

1. Enable Developer Options in Android Settings.
2. Under **Select mock location app**, choose **Where am I ??**.
3. Turn on device Location and grant the app location permission.
4. Search for an address or enter coordinates, select a result, and tap **Start**.
5. Tap **Stop** to restore normal location providers.
6. Tap the search field for history, or open **Favorites**. Use **⋮ → Rename** to customize a favorite's label.

Android continues to mark these locations as mock. This app is intended for development and testing; it does not conceal mock-location status.

## Tests

```sh
./gradlew testDebugUnitTest lintDebug
npm ci --prefix tests
npm test --prefix tests
```

The Kotlin suite covers history ordering and limits, persistence across restarts, independent history/favorite removal, malformed records, invalid names, and Unicode labels. The map test covers marker updates, invalid coordinates, attribution, and error/retry behavior.

The version 1.5 build previously passed `assembleDebug`, all six JVM tests, and `lintDebug`. That validation did not include a physical-device test. The GitHub workflow checks the public-source build and tests separately.

## Privacy and limitations

- No application account, app backend, analytics, or advertising SDK is included.
- History and favorites are stored locally. Address searches use the device geocoding service, and map tiles are fetched from OpenStreetMap.
- Search availability and address language depend partly on the device's geocoder. The map requires internet access.
- This is a development portfolio project, not a Play Store release. The mock permission is declared in the debug build.
- Map tile usage must respect the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/) before wider distribution.

## Credits

Built by [DhimasAlfathan](https://github.com/DhimasAlfathan), with AI-assisted implementation and iteration.

Mapping uses [Leaflet](https://leafletjs.com/) and © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). The bundled Leaflet license is preserved in [LEAFLET-LICENSE.txt](app/src/main/assets/LEAFLET-LICENSE.txt).
