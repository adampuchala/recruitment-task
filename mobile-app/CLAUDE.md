# CLAUDE.md

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Recruitment adaptation — authoritative scope

This module is a recruitment adaptation of the open-source [KotlinConf App](https://github.com/JetBrains/kotlinconf-app). It retains the upstream Kotlin Multiplatform, Compose and Android/iOS UI foundation, but its active product scope is a simple banking client that consumes the Mobile BFF in the parent repository.

Web, WebAssembly and Desktop modules from the upstream project have been removed intentionally. Do not restore or document them as supported targets. The active targets are Android and iOS, and the shared screen code belongs in `app/shared/src/commonMain`.

The active banking flow is intentionally small and unauthenticated: create account, retrieve account details and deposit funds. The BFF is expected at `http://10.0.2.2:8080` for Android Emulator and `http://localhost:8080` for iOS Simulator.

Historical upstream descriptions later in this file are reference material only; this section, the root `README.md`, and the current Gradle settings take precedence.

## Build & Run Commands

```bash
# Generate request/response models from the local BFF OpenAPI contract
./gradlew :mobileapp-api-models:generateKotlinxModels

# Compile the Android application
./gradlew :app:androidApp:compileDebugKotlin

# Compile the shared iOS Simulator target
./gradlew :app:shared:compileKotlinIosSimulatorArm64
```

Android uses the `app.androidApp` run configuration in IDE; iOS uses `MobileAppScheme`.

## Module Structure

```
:mobileapp-api-models — Kotlin Multiplatform API request/response models generated from `../mobile-bff/openapi.yaml`.
                        Do not edit `build/generated`; regenerate after contract changes.

:core                 — Retained shared upstream support models. No UI or banking API ownership.

:app:ui-components    — Reusable Compose UI components (MobileAppTheme, typography, colors, buttons, cards, etc.)
                        Has its own resource class (UiRes) separate from the main app.

:app:shared           — Main Android/iOS KMP client. Contains the banking API client, service,
                        ViewModel and common Compose screen.

:app:androidApp       — Android entry point only. Wires up MobileAppGraph.
```

## OpenAPI and copyright

`mobile-bff/openapi.yaml` is the canonical API contract. `mobileapp-api-models` generates the DTOs used by `BankApiClient` directly from that file; it contains models only, not a generated HTTP client. Do not create or maintain a second copy of the specification under `mobile-app`.

The source code and build configuration of `mobileapp-api-models` are recruitment-specific code covered by the same notice and recruitment-only rule as the backend repository:

```text
Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
```

Do not add this notice to upstream or third-party generated code where doing so would alter its license or generator output. Do add it to new or modified recruitment-specific source, configuration and documentation files where the format supports comments or metadata.

## Archived upstream reference — do not use for recruitment implementation

The remaining sections describe the original KotlinConf App. They are retained only for provenance of imported code and must not be treated as requirements for this banking client.

### Client Architecture

**Dependency Injection — Metro**

The app uses [Metro](https://github.com/ZacSweers/metro) (not Koin) for DI on the client. Key interfaces:

- `AppGraph` — top-level DI graph (`AppScope`), one per app process. Holds `ConferenceService`, `FlagsManager`, `TimeProvider`, etc.
- `YearGraph` — a *scoped sub-graph* (`YearScope`) created per active conference year. Holds `YearlyApi` and `YearlyStorage`. Created by `YearGraph.Factory` inside `ConferenceService`.
- Platform-specific graph (e.g. `MobileAppGraph`) — annotated `@DependencyGraph(AppScope::class)`, created at app startup, implements `AppGraph`.

ViewModels are registered into Metro's map multibinding:
```kotlin
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ScheduleViewModel(...) : ViewModel()
```

**ConferenceService**

`ConferenceService` is the central app singleton. It:
- Fetches `AppConfig` (current year) from the backend on startup and stores it locally.
- Creates a `YearGraph` for the active year, giving access to that year's `YearlyApi` and `YearlyStorage`.
- Exposes `StateFlow`s: `agenda`, `speakers`, `conferenceInfo`, `goldenKodeeData`, `votes`, `currentYear`.
- Handles favorites, voting, policy acceptance, notification scheduling, and asset caching.

**Navigation**

Uses `androidx.navigation3`. Routes are `@Serializable sealed interface AppRoute` types defined in `Routes.kt`. `TopLevelRoute` marks bottom-nav destinations. `NavHost.kt` maps routes to screen composables.

**Screens / ViewModels pattern**

Each screen has a corresponding `*ViewModel` that takes `ConferenceService` as a dependency, maps service `StateFlow`s into `uiState: StateFlow<ErrorLoadingState<...>>`, and exposes user-action functions. The screen Composable receives the ViewModel via Metro's ViewModel factory.

**Flags**

`Flags` is a `@Serializable data class` stored via `FlagsManager`. Compose screens access it via `LocalFlags`. Includes developer-mode options like `useFakeTime`, `debugLogging`, and `useFakeGoldenKodeeData`.

**Source Set Hierarchy**

The `app:shared` module defines custom intermediate source sets beyond the default hierarchy:
- `nonAndroidMain` — shared by `iosMain`, `jvmMain`, and `webMain` (excludes Android)
- `nonWebMain` — shared by `androidMain`, `iosMain`, and `jvmMain` (has `okio` dependency; web can't use it)
- `webMain` — shared by `wasmJsMain` and `jsMain` (JS-specific timezone shim, browser APIs)

### Backend Architecture

- **Framework**: Ktor with Netty engine
- **DI**: Koin (`diModule()` in `DiModule.kt`)
- **Database**: Exposed ORM + HikariCP. PostgreSQL in production; H2 file-based fallback when no `database.host` is configured (used for local dev and tests).
- **Migrations**: `MigrationRunner` applies SQL migrations in order.

**Route structure** (`RoutesModule.kt`):
- Year-agnostic: `/healthz`, `/time`, `/admin/*`, `/config`
- Year-prefixed: `/{year}/conference`, `/{year}/conference-info`, `/{year}/schedule`, `/{year}/vote`, `/{year}/sign`, `/{year}/golden-kodee`, `/{year}/documents/*`, etc.

**Data flow**: `SessionizeService` polls the Sessionize API on a configurable interval and caches schedule data in-memory. `MobileAppRepository` handles all DB operations (users, votes, feedback, signed policies) using `suspendTransaction`.

### Key Conventions

**Version management**: Version numbers must stay in sync across Android `build.gradle.kts`, iOS `project.pbxproj`, iOS `Info.plist`, and `app/shared/src/commonMain/composeResources/values/version.xml`. Use `./gradlew prepareRelease` (which calls `updateVersion` and `exportLibraryDefinitions`) rather than editing these files manually.

**Resources**: `app:shared` uses resource class `com.adampuchala.mobileapp.generated.resources` (auto-generated). `app:ui-components` uses its own `UiRes` / `com.adampuchala.mobileapp.ui.generated.resources`. Don't mix them.

**Backend tests**: Use `ktor-server-test-host` with `test-application.yaml` config (H2 database, no external dependencies).
