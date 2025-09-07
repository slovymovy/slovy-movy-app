Project development guidelines

## Overview
Kotlin Multiplatform (KMP) workspace with 3 modules:
- **composeApp**: Compose Multiplatform UI (Android, Desktop, iOS)
- **shared**: KMP shared library
- **server**: JVM Ktor server

**Stack**: Gradle, Kotlin, Compose Multiplatform, AGP, Ktor, SqlDelight

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
- **Android instrumented tests**: `gradlew :composeApp:connectedAndroidTest`
- **Server tests**: `gradlew :server:test`
- **Android APK**: `gradlew :composeApp:assembleDebug`
- **Desktop distribution**: `gradlew :composeApp:packageDistributionForCurrentOS`

## Test Structure
- **shared**: `shared/src/commonTest` (kotlin-test)
  - Platform-specific: `shared/src/jvmTest`, `shared/src/iosTest`
- **composeApp**: `composeApp/src/commonTest` (kotlin-test)
  - Android instrumented: `composeApp/src/androidTest` (androidx.test)
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

## Database (SqlDelight)
- App DB schema: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/`
  - Migrations: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/migrations/`
- Dictionary DB schema: `shared/src/commonMain/sqldelight/dictionarydb/com/slovy/slovymovyapp/dictionary/`
- Translation DB schema: `shared/src/commonMain/sqldelight/translationdb/com/slovy/slovymovyapp/translation/`
- Repository pattern: `SettingsRepository` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/settings/`
- Platform drivers: DriverFactory implementations for Android/iOS/JVM

## Testing Guidelines
- Do not leave println statements in tests.
  - Prefer descriptive assertion messages (assertTrue, assertEquals, fail with context) to convey failures.
  - If you need temporary debugging during local development, use a debugger or temporary logs and remove them before committing.
- Fail fast in tests: do not aggregate errors.
  - Validate items inside loops using immediate assertions; abort the test on the first failure.
  - Avoid collecting errors into lists and failing at the end.

## Key Notes
- Module accessors: :composeApp, :shared, :server
- iOS warnings on non-macOS are expected and harmless
- Compose Hot Reload plugin available for development