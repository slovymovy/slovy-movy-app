Project development guidelines

## Overview
Kotlin Multiplatform (KMP) workspace with 3 modules:
- **composeApp**: Compose Multiplatform UI (Android, Desktop, iOS)
- **shared**: KMP shared library
- **server**: JVM Ktor server

**Stack**: Gradle, Kotlin, Compose Multiplatform, AGP, Ktor

## Build Commands
**WSL Note**: Use `cmd.exe /c gradlew.bat` prefix (Windows paths in output)

### Core Tasks
- **All tests**: `gradlew test`
- **Build all**: `gradlew build`
- **Run desktop**: `gradlew :composeApp:run`
- **Run server (dev)**: `gradlew -Pdevelopment :server:run`

### Module-Specific Tasks
- **Shared tests**: `gradlew :shared:allTests`
- **ComposeApp tests**: `gradlew :composeApp:allTests`
- **Server tests**: `gradlew :server:test`
- **Android APK**: `gradlew :composeApp:assembleDebug`
- **Desktop distribution**: `gradlew :composeApp:packageDistributionForCurrentOS`

## Test Structure
- **shared**: `shared/src/commonTest` (kotlin-test)
- **composeApp**: `composeApp/src/commonTest` (kotlin-test)
- **server**: `server/src/test` (ktor-server-test-host, kotlin-test-junit)

## Configuration
- **Versions**: All in `gradle/libs.versions.toml`
- **JVM target**: 11 for Android/Shared
- **iOS**: Disabled on non-macOS (expected behavior)
- **Android SDK**: compileSdk=36, minSdk=24, targetSdk=36

## Code Structure
- **KMP source sets**: commonMain/commonTest for shared code
- **shared module**: Cross-platform APIs (e.g., Greeting.greet(), Constants.kt)
- **Compose UI**: composeApp/src/commonMain
- **Android namespace**: com.slovy.slovymovyapp
- **Server main**: com.slovy.slovymovyapp.ApplicationKt

## Key Notes
- Module accessors: :composeApp, :shared, :server
- iOS warnings on non-macOS are expected and harmless
- Compose Hot Reload plugin available for development