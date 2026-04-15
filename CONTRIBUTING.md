# Contributing to Tides

Thank you for your interest in contributing! This guide will help you get started.

## Development Setup

### Prerequisites

- Latest stable Android Studio (Meerkat or newer recommended for Kotlin 2.3)
- JDK 21
- Android SDK with Wear OS system images
- A Wear OS device or emulator

### Getting Started

1. Fork and clone the repository
2. Copy `gradle.properties.example` to `gradle.properties` and fill in your Tidal API credentials
3. Open the project in Android Studio
4. Sync Gradle and build

### Building

```bash
./gradlew assembleDebug
```

### Running Tests

```bash
./gradlew test
```

## Code Style

- Follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use Clean Architecture: data → domain → presentation
- ViewModels use MVI pattern (UiState / UiEvent / UiEffect)
- Never do work in ViewModel `init` — use `LaunchedEffect` triggers
- Navigation logic stays in ViewModels, not in NavGraph

## Pull Request Process

1. Create a feature branch from `main`
2. Make your changes with clear, focused commits
3. Ensure `./gradlew assembleDebug` and `./gradlew test` pass
4. Open a PR with a clear description of what and why
5. Wait for review

## Reporting Issues

- Use GitHub Issues for bug reports and feature requests
- Include device model, Wear OS version, and steps to reproduce for bugs

## License

By contributing, you agree that your contributions will be licensed under the GPL-3.0 License.
