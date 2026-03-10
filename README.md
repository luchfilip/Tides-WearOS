# Tides: Unofficial Tidal for Wear OS

An unofficial Tidal client for Wear OS, built with Kotlin and Jetpack Compose.

> **Disclaimer:** This project is not affiliated with, endorsed by, or connected to TIDAL, Block Inc., or any of their subsidiaries. "Tidal" is a trademark of Block Inc. This app requires an active Tidal subscription. Use at your own risk.

## Screenshots

<!-- TODO: Add screenshots -->

## Features

- Browse your Tidal library (albums, playlists, mixes)
- Stream music directly on your Wear OS watch
- Now Playing controls with track info and artwork
- Search for tracks, albums, and playlists
- Device Code authentication flow

## Setup

1. Clone the repository
2. Copy `gradle.properties.example` to `gradle.properties`
3. Fill in your Tidal API credentials (client ID and secret)
4. Build and install:
   ```bash
   ./gradlew assembleDebug
   ```

## Architecture

The project follows Clean Architecture with MVI pattern, organized into 7 Gradle modules:

| Module | Description |
|--------|-------------|
| `app` | Main application, navigation, DI |
| `core` | Shared UI theme, networking, data stores |
| `feature-auth` | Device Code authentication flow |
| `feature-player` | Audio playback with DASH manifest support |
| `feature-library` | Library browsing (albums, playlists, mixes) |
| `feature-offline` | Offline downloads (planned) |
| `feature-settings` | User settings and preferences |

**Key technologies:** Kotlin 2.3, Jetpack Compose for Wear OS, Hilt, Retrofit, Media3, Room, DataStore

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and contribution guidelines.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
