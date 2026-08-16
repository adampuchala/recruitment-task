# Recruitment Bank Mobile App

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

This Kotlin Multiplatform mobile client is a recruitment adaptation based on the open-source [KotlinConf App](https://github.com/JetBrains/kotlinconf-app). The upstream project provides the Compose Multiplatform, Android/iOS and UI-component foundation. This adaptation replaces its conference flows with a small banking client consuming the local Banking BFF.

The Web and Desktop application modules from the upstream project were intentionally removed. The supported targets in this repository are Android and iOS.

## Banking functionality

The common Compose UI supports:

- account creation;
- account lookup and balance display;
- deposits;
- loading, input validation and API-error states.

The app calls the unauthenticated Mobile BFF at port `8080`. On Android Emulator it uses `http://10.0.2.2:8080`; on iOS Simulator it uses `http://localhost:8080`.

Start the Banking BFF and its dependencies in the sibling backend repository before running the app:

```bash
cd ../hapoalim-recruitment-bank-system
./gradlew clean build
docker compose up --build
```

## OpenAPI models and copyright

The API contract is [openapi.yaml](openapi.yaml). It mirrors the BFF contract from `mobile-bff/openapi.yaml` in the backend repository. The `mobileapp-api-models` module generates Kotlin Multiplatform request and response models from that contract:

```bash
./gradlew :mobileapp-api-models:generateKotlinxModels
```

Do not edit files below `mobileapp-api-models/build/generated` manually. The source and build configuration of `mobileapp-api-models`, along with all recruitment-specific code in this repository, are covered by:

```text
Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
```

This is the same copyright notice and recruitment-only usage rule used by the backend repository. It does not change the licenses or copyright notices of the upstream KotlinConf App or third-party dependencies.

## Build

```bash
# Generate models and compile the Android application
./gradlew :app:androidApp:compileDebugKotlin

# Verify shared iOS Simulator sources
./gradlew :app:shared:compileKotlinIosSimulatorArm64
```

Run Android from Android Studio using the `app.androidApp` configuration. Run iOS from Xcode using `MobileAppScheme`.
