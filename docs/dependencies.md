# Buddy AI Assistant - Dependencies

This document provides a concise overview of the external libraries used in the Buddy application.

## Shared (`src/common`)

| Library | Category | Version | Purpose |
| :--- | :--- | :--- | :--- |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Concurrency | 1.10.2 | Coroutines, flows, and the conversation-engine mutex (shared by app and CLI) |
| `com.squareup.okhttp3:okhttp` | Networking | 5.3.2 | HTTP client for LLM, web search, and URL fetching |
| `com.google.code.gson:gson` | Data | 2.14.0 | JSON serialization/deserialization (providers, sessions, summaries) |
| `org.jsoup:jsoup` | Data | 1.22.2 | HTML parsing for URL content fetching |
| `org.slf4j:slf4j-api` | Logging | 2.0.17 | Logging facade used by the shared core (`Log`) |

## Android (`src/android`, module `:app`)

| Library | Category | Version | Purpose |
| :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` | Core | 1.18.0 | Kotlin extensions for Android core |
| `androidx.compose.*` | UI Framework | BOM 2026.05.00 | Declarative UI toolkit (Material 3, Graphics, Tooling, icons) |
| `androidx.compose.material:material-icons-extended` | UI Framework | via BOM | Extended icon set used across screens |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle | 2.10.0 | Lifecycle-aware components |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | Lifecycle | 2.10.0 | ViewModel integration for Compose |
| `androidx.activity:activity-compose` | Lifecycle | 1.13.0 | Activity integration for Compose |
| `io.coil-kt:coil-compose` | Images | 2.7.0 | Image loading and caching |
| `io.noties.markwon:*` | Text | 4.6.2 | Markdown rendering with tables, strikethrough, task lists, HTML, linkify, Coil images (pinned in `android/build.gradle.kts`) |
| `androidx.security:security-crypto` | Security | 1.0.0 | EncryptedSharedPreferences for API key storage |
| `androidx.datastore:datastore-preferences` | Storage | 1.2.1 | Persistent app settings and saved sessions (non-sensitive) |
| `androidx.work:work-runtime-ktx` | Background | 2.11.2 | Deferrable background tasks (connectivity checks) |

## Desktop CLI (`src/cli`, module `:cli`)

| Library | Category | Version | Purpose |
| :--- | :--- | :--- | :--- |
| `org.jline:jline` | CLI | 3.25.1 | Raw terminal input for the standalone desktop CLI application |
| `ch.qos.logback:logback-classic` | Logging | 1.5.18 | SLF4J backend for desktop logging (`DesktopLogger`) |

## Test (`src/common/test`)

| Library | Category | Version | Purpose |
| :--- | :--- | :--- | :--- |
| `junit` | Testing | 4.13.2 | Desktop-JVM unit tests (`:cli:test`) |
| `androidx.test.ext:junit` | Testing | 1.3.0 | Android instrumented test extensions |

**Note**: Versions are managed through the Gradle version catalog (`gradle/libs.versions.toml`), except Markwon, which is pinned directly in `android/build.gradle.kts`.
