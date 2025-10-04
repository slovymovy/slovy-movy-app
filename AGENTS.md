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

- **shared**: Core logic lives in `shared/src/commonMain`; use the composeApp tests to cover DB behaviour.
- **composeApp**: Shared tests live in `composeApp/src/commonTest`. The expect/actual `BaseTest` + `TestContext` wiring
  lets the same tests run on every target:
    - Android JVM (`gradlew :composeApp:androidUnitTest`) uses Robolectric to provide an Android context.
    - Android instrumented (`gradlew :composeApp:connectedAndroidTest`) reuses the same `commonTest` sources; requires
      an emulator/device and network access for DB download tests.
    - Desktop JVM (`gradlew :composeApp:desktopTest`) exercises the same tests against the JDBC driver.
    - iOS simulator (`gradlew :composeApp:iosSimulatorArm64Test`) runs the tests on macOS; native targets are skipped
      elsewhere.
- **server**: `server/src/test` (ktor-server-test-host, kotlin-test-junit)

### Adding Tests

- Default to `composeApp/src/commonTest` so logic executes on Android JVM, Android instrumented, Desktop, and iOS.
- Extend `BaseTest` for KMP tests; it injects the platform context through `TestContext`.
- If a test needs target-specific setup, update the relevant `TestContext.<platform>.kt` actual or add a new
  expect/actual helper alongside `BaseTest`.
- Only add platform-specific test source sets when behaviour truly diverges (e.g., Android UI instrumentation);
  otherwise keep coverage centralized in `commonTest`.

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

### Compose UI workflow

- Split each screen into a thin stateful entry point and a stateless composable that renders a `UiState` data model;
  previews/tests should target the stateless layer.
- Keep all mutable UI flags (loading, expanded sections, dialog visibility, etc.) inside the `UiState`; avoid
  `remember`/`rememberSaveable` inside rendering composables.
- Provide explicit callbacks (`onToggle`, `onRetry`, …) so the orchestrator can mutate the `UiState` while previews pass
  no-op lambdas.
- Add preview functions for every meaningful `UiState` variant (content, loading, error, empty) so designers/devs can
  inspect layouts without runtime wiring.
- When deriving default UI state from domain models (e.g., `LanguageCard`), add helper mappers (`toUiState()`) rather
  than embedding logic inside composables.

### ViewModel pattern

- Every screen should use a ViewModel to manage state and survive configuration changes.
- Create a `<ScreenName>ViewModel` class that extends `ViewModel` and holds the screen's `UiState`.
- State should be exposed as `var state by mutableStateOf(...)` with `private set`.
- Store scroll states (`LazyListState`, `ScrollState`) in the ViewModel to preserve scroll position across navigation.
- The screen composable receives the ViewModel as a parameter: `fun Screen(viewModel: ScreenViewModel)`.
- In `App.kt`, create ViewModels using `viewModel(viewModelStoreOwner = backStackEntry) { ScreenViewModel(...) }` to
  scope them to the navigation entry.
- The stateless `*Content` composable receives `state` and `scrollState` as parameters with default values for previews.
- Example structure:
  ```kotlin
  data class ScreenUiState(...)

  class ScreenViewModel(...) : ViewModel() {
      var state by mutableStateOf(ScreenUiState(...))
          private set
      val scrollState = LazyListState() // or ScrollState(0)

      fun updateState(...) { state = state.copy(...) }
  }

  @Composable
  fun Screen(viewModel: ScreenViewModel, ...) {
      ScreenContent(
          state = viewModel.state,
          scrollState = viewModel.scrollState,
          ...
      )
  }

  @Composable
  fun ScreenContent(
      state: ScreenUiState,
      scrollState: LazyListState = LazyListState(),
      ...
  ) { ... }
  ```

## Database (SqlDelight)

- App DB schema: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/`
    - Migrations: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/migrations/`
- Dictionary DB schema: `shared/src/commonMain/sqldelight/dictionarydb/com/slovy/slovymovyapp/dictionary/`
- Translation DB schema: `shared/src/commonMain/sqldelight/translationdb/com/slovy/slovymovyapp/translation/`
- Repository pattern: `SettingsRepository` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/settings/`
- Database bootstrap: `DatabaseProvider` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/db/`
- Platform DB support: expect/actual `PlatformDbSupport` + helpers in
  `composeApp/src/*/kotlin/com/slovy/slovymovyapp/data/remote/`

## Testing Guidelines

- Do not leave println statements in tests.
    - Prefer descriptive assertion messages (assertTrue, assertEquals, fail with context) to convey failures.
    - If you need temporary debugging during local development, use a debugger or temporary logs and remove them before
      committing.
- Fail fast in tests: do not aggregate errors.
    - Validate items inside loops using immediate assertions; abort the test on the first failure.
    - Avoid collecting errors into lists and failing at the end.

## Key Notes

- Module accessors: :composeApp, :shared, :server
- iOS warnings on non-macOS are expected and harmless
