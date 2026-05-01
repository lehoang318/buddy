# Buddy AI Assistant - Dependencies

This document provides a concise overview of the external libraries used in the Buddy application.

| Library | Category | Version | Purpose |
| :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` | Core | 1.10.1 | Kotlin extensions for Android core |
| `androidx.compose.*` | UI Framework | BOM 2026.02.01 | Declarative UI toolkit (Material 3, Graphics, Tooling, icons) |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle | 2.6.1 | Lifecycle-aware components |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | Lifecycle | 2.10.0 | ViewModel integration for Compose |
| `androidx.activity:activity-compose` | Lifecycle | 1.8.0 | Activity integration for Compose |
| `com.squareup.okhttp3:okhttp` | Networking | 5.3.2 | HTTP client for LLM API calls |
| `com.google.code.gson:gson` | Data | 2.13.2 | JSON serialization/deserialization |
| `org.jsoup:jsoup` | Data | 1.22.1 | HTML parsing for URL content fetching |
| `io.coil-kt:coil-compose` | Images | 2.7.0 | Image loading and caching |
| `io.noties.markwon:*` | Text | 4.6.2 | Markdown rendering with tables, strikethrough, task lists, HTML, linkify, Coil images |
| `androidx.datastore:datastore-preferences` | Storage | 1.2.1 | Persistent user settings |
| `androidx.work:work-runtime-ktx` | Background | 2.9.1 | Deferrable background tasks (connectivity checks) |

| `junit` / `androidx.test.*` | Testing | 4.13.2 / 1.1.5 | Unit and UI testing framework |

**Note**: Versions are managed through the Gradle version catalog (`libs.versions.toml`).
