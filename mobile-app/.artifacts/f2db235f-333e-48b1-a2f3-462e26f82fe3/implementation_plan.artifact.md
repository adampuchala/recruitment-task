# Rebranding KotlinConf to SampleApp

This plan outlines the steps to rebrand the application, including package renames, project name changes, and application identification updates across Android and iOS.

## User Review Required

> [!IMPORTANT]
> This is a large-scale refactor that involves renaming packages globally. It will move almost every Kotlin file to a new directory structure.
> Existing local developer configurations or uncommitted changes may need manual merging after this operation.

## Proposed Changes

### Project Configuration

#### [MODIFY] [settings.gradle.kts](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/settings.gradle.kts)
- Change `rootProject.name` from `"KotlinConfApp"` to `"SampleApp"`.

#### [MODIFY] [gradle.properties](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/gradle.properties)
- No direct name references found, but verified.

---

### Android Application (`:app:androidApp`)

#### [MODIFY] [build.gradle.kts](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/androidApp/build.gradle.kts)
- Change `namespace` to `"com.jetbrains.sampleapp"`.
- Change `applicationId` to `"com.jetbrains.sampleapp"`.

#### [MODIFY] [AndroidManifest.xml](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/androidApp/src/main/AndroidManifest.xml)
- Update all class references to the new package structure.

#### [MODIFY] [strings.xml](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/androidApp/src/main/res/values/strings.xml)
- Change `app_name` to `"SampleApp"`.

---

### Shared Library (`:app:shared`)

#### [MODIFY] [build.gradle.kts](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/shared/build.gradle.kts)
- Change `namespace` to `"org.jetbrains.sampleapp"`.
- Change `packageOfResClass` to `"org.jetbrains.sampleapp.generated.resources"`.

---

### UI Components Library (`:app:ui-components`)

#### [MODIFY] [build.gradle.kts](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/ui-components/build.gradle.kts)
- Change `namespace` to `"com.jetbrains.sampleapp.ui"`.
- Change `packageOfResClass` to `"org.jetbrains.sampleapp.ui.generated.resources"`.

---

### Core Library (`:core`)

#### [MODIFY] [build.gradle.kts](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/core/build.gradle.kts)
- Change `namespace` to `"com.jetbrains.sampleapp.core"`.

---

### Code Refactoring (All Modules)

#### [MODIFY] All Kotlin files
- Replace `package org.jetbrains.kotlinconf...` with `package org.jetbrains.sampleapp...`.
- Replace `package com.jetbrains.kotlinconf...` with `package com.jetbrains.sampleapp...`.
- Update all import statements accordingly.
- Move files to their new directory structures (e.g., `src/commonMain/kotlin/org/jetbrains/sampleapp/...`).

---

### iOS Configuration

#### [MODIFY] [project.pbxproj](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/iosApp/KotlinConf.xcodeproj/project.pbxproj)
- Rename product from `KotlinConf` to `SampleApp`.
- Change `PRODUCT_BUNDLE_IDENTIFIER` to `com.sampleapp.iosapp`.

#### [MODIFY] [Info.plist](file:///Users/ad.puchala/Downloads/kotlinconf-app-main/app/iosApp/iosApp/Info.plist)
- Update display names and other identifiers.

## Verification Plan

### Automated Tests
- Run `./gradlew assemble` to ensure all modules build with new namespaces.
- Run unit tests to verify package renames didn't break DI or resource access.

### Manual Verification
- Deploy to Android Emulator to verify the app name is "SampleApp".
- Check that all screens still render correctly (resource access verification).
