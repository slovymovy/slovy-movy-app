Project development guidelines

## Overview

Kotlin Multiplatform (KMP) workspace with 3 modules:

- **composeApp**: Compose Multiplatform UI (Android, Desktop, iOS)
- **shared**: KMP shared library
- **server**: JVM Ktor server

**Stack**: Gradle, Kotlin, Compose Multiplatform, AGP, Ktor, SqlDelight, Valkyrie

## Build Commands

**Environment Notes**:

- **MSYS/Git Bash (native Windows)**: Use `./gradlew.bat` directly
- **WSL**: Use `cmd.exe /c gradlew.bat` prefix (Windows paths in output)

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
- **Java version**: Use Java 21 (via sdkman: `sdk use java 21.0.9-amzn`). Java 25+ may cause Kotlin compatibility
  issues.
- **Data version**: `DataDbManager.VERSION = "v11"`; when bumping, upload DBs under the new prefix in the GCS bucket and
  keep the version in sync.

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

#### Preview Functions

- All `@Preview` functions must support both light and dark themes using the themed preview pattern.
- Use `@PreviewParameter(ThemePreviewProvider::class) isDark: Boolean` to receive theme parameter.
- Wrap preview content with `ThemedPreview(darkTheme = isDark) { ... }` to apply the theme.
- Import required types: `PreviewParameter` from `androidx.compose.ui.tooling.preview.PreviewParameter`.
- The `ThemePreviewProvider` and `ThemedPreview` are defined in
  `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/ui/Preview.kt`.

Example:

```kotlin
@Preview
@Composable
private fun MyScreenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        MyScreenContent(state = MyUiState(...))
    }
}
```

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

### Localization

- Localization uses Compose Multiplatform resources from `composeApp/src/commonMain/composeResources/`.
- Base locale is `values/strings.xml`.
- Supported locales are separate folders (for example `values-ru`, `values-nl`, `values-pl`).
- App language follows the OS locale (no in-app language override).

#### Adding or changing text

- For UI text in composables, use `stringResource(Res.string.<key>)`.
- Localize accessibility text too (`contentDescription`, `onClickLabel`, `stateDescription`).
- For parameterized strings, use placeholders in XML (`%1$s`, `%1$d`) and pass args from code.
- If pluralization is needed, add `<plurals>` resources instead of manual `"s"` suffix logic.
- Keep user-visible copy out of Kotlin literals in `commonMain` UI code.
- Preview-only literals are acceptable in `@Preview` functions.

#### Non-composable and shared text

- `stringResource(...)` is composable-only. For non-composable flows, prefer passing localized text from UI layer.
- If text must be represented before rendering, use the `UiText` pattern in
  `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/i18n/` and resolve at the composable boundary.

#### Platform-specific localization

- Android platform UI surfaces (notifications, foreground service channel names, chooser titles) must be localized too.
- iOS app metadata shown by the system (for example display name and permission copy) should use localized
  `InfoPlist.strings` where applicable.
- Keep Compose resources as source of truth for shared UI, and use native platform resources only for
  platform-owned surfaces.

#### Quality gates

- Key parity check task: `gradlew :composeApp:verifyLocalizationKeys`.
- CI runs the same parity task and fails if any locale is missing/has extra keys vs base `values/`.
- Hardcoded UI string CI check is currently not enabled; enforce this in code review and local checks.

### SVG Icons (Valkyrie)

- The [Valkyrie Gradle plugin](https://github.com/ComposeGears/Valkyrie) converts SVG files into Compose `ImageVector`
  constants at build time.
- **Source SVGs**: `composeApp/src/commonMain/valkyrieResources/` — drop `.svg` files here (no spaces in filenames).
- **Generated output**: `composeApp/build/generated/sources/valkyrie/` (not committed to git).
- **Icon pack**: `SlovyIcons` in package `com.slovy.slovymovyapp.ui.icons`; access icons as `SlovyIcons.IconName`.
- **Generation task**: `generateValkyrieImageVector` runs automatically before Kotlin compilation.
- **Import pattern**: Extension properties require importing both the pack and the icon:
  ```kotlin
  import com.slovy.slovymovyapp.ui.icons.SlovyIcons
  import com.slovy.slovymovyapp.ui.icons.MyIcon
  // then use: SlovyIcons.MyIcon
  ```
- **Icon vs Image**: Use `Icon()` for simple monochrome icons (applies tint). Use `Image()` for multi-color
  illustrations — `Icon()` flattens colors into a solid tint, making detailed SVGs appear as filled rectangles.
- The `EmptyState` component has two overloads: one taking `ImageVector` (renders with `Icon` + tint), and one taking
  `iconContent: @Composable () -> Unit` for custom rendering (e.g., `Image()` for illustrations).

### Speech / TTS

- `TextToSpeechManager` has platform actuals (Android, iOS, desktop no-op) and emits word-boundary + status callbacks.
- Voice filtering is stored in settings under `Setting.Name.ENABLED_VOICES` (JSON per language). `VoiceFilterHelper`
  loads/saves the enabled IDs and defaults to local (offline) voices when first seen.
- Allow users to open system TTS settings via `openSettings()`; Android uses install/check intents, iOS opens
  Accessibility Speech if allowed.

## Database (SqlDelight)

### Schema Locations

- App DB schema: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/`
    - Migrations: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/migrations/`
    - Verification DB: `shared/src/commonMain/sqldelight/appdb/<version>.db` (e.g., `2.db`)
- Dictionary DB schema: `shared/src/commonMain/sqldelight/dictionarydb/com/slovy/slovymovyapp/dictionary/`
- Translation DB schema: `shared/src/commonMain/sqldelight/translationdb/com/slovy/slovymovyapp/translation/`
- Repository pattern: `SettingsRepository` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/settings/`
- Database bootstrap: `DatabaseProvider` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/db/`
- Platform DB support: expect/actual `PlatformDbSupport` + helpers in
  `composeApp/src/*/kotlin/com/slovy/slovymovyapp/data/remote/`
- Local writable DBs: `local_dictionary.db` and `local_translation.db` via `LocalDbManager`.
- Downloaded read-only DBs live in the platform database dir and are cached via `ReadOnlyDatabaseCache`; use the cache
  helpers so drivers get closed when deleting files.
- `DataDbManager` enforces query-only mode for read-only drivers, checks available disk before downloads, and writes to
  a `.part` temp file before renaming.

### Migrations

- Migration files are named `<version>.sqm` (e.g., `1.sqm` to migrate from version 1 to 2)
- Stored in the `migrations/` subdirectory alongside schema files
- Contain SQL statements to upgrade database schema
- Verification `.db` files (e.g., `2.db`) represent the expected schema after migrations
- Verification tasks: `gradlew :shared:verifyCommonMainAppDatabaseMigration`
    - **Windows Note**: Migration verification is disabled on Windows due to
      [SqlDelight issue #5312](https://github.com/sqldelight/sqldelight/issues/5312)
    - Configured in `shared/build.gradle.kts` with `verifyMigrations.set(!OperatingSystem.current().isWindows)`
    - On non-Windows platforms, the verification task confirms migrations produce the expected schema

### Ingestion determinism

- `JsonIngestionBuilder` uses deterministic IDs: lemma/lemma_pos derived from MD5 of lemma + normalized lemma + POS;
  other IDs come from input JSON; duplicates or existing lemma IDs fail.
- Requires Zipf frequency for every lemma; ingestion fails if the lemma is missing from the frequency map.
- Prefers native raw entries per `LANG_TO_SOURCE_FILE` for forms/POS mapping; forms deduplicated by form + normalized
  form + tags (first occurrence wins).
- `sense_id` duplicates across raw entries are errors; UUID parsing pads incomplete IDs to keep ingestion resilient.
- Online-only lemmas are ingested from raw data first; processed data can be added later via `ingestProcessedOverRaw`,
  and translations-only ingestion is supported once senses exist.
- When streaming words from the server, `DictionaryClient` ingests base → translated stages into local DBs, copying raw
  rows from downloaded DBs first if needed.

## External API Clients (Server Module)

### API Key/Token Management

All external API clients follow a consistent pattern for credentials:

1. **Environment variable** (checked first, preferred for CI)
2. **Local file** (fallback for local development)

| Service | Environment Variable | File Location                         |
|---------|----------------------|---------------------------------------|
| OpenAI  | `OPENAI_API_KEY`     | `.openai_api_key`                     |
| Gemini  | `AISTUDIO_KEY`       | `.aistudio_key`                       |
| GitHub  | `ACCESS_TO_GH_TOKEN` | `server/.github_key` or `.github_key` |

All key files are in `.gitignore`. For CI, secrets are configured in GitHub Actions.

### AI Providers

Located in `server/src/main/kotlin/com/slovy/slovymovyapp/server/ai/`:

- **AIProvider interface**: Common interface for AI completions with caching and retry support
- **OpenAIProvider**: OpenAI API integration (`OpenAI.kt`)
- **GeminiProvider**: Google AI Studio integration (`Gemini.kt`)
- **Enhancers**: `enhancer/` subdirectory contains domain-specific AI enhancement logic

Pattern for new providers:

```kotlin
object MyProvider {
    fun clientProvider(): () -> Client = {
        val apiKey = System.getenv("MY_API_KEY")?.takeIf { it.isNotBlank() } ?: run {
            val keyFile = File(".my_api_key")
            require(keyFile.exists()) { "Missing .my_api_key file and MY_API_KEY env var" }
            keyFile.readText().trim()
        }
        // Create and return client
    }
}
```

### GitHub Client

Located in `server/src/main/kotlin/com/slovy/slovymovyapp/server/github/GitHubClient.kt`:

- Uses `org.kohsuke:github-api` SDK
- Pre-configured to access `slovymovy/words` repository
- Reads larger files via `downloadUrl` when GitHub returns encoding `none`
- Main methods:
    - `isAvailable()`: Check if token is configured
    - `getToken()`: Get the configured token
    - `loadDbExtractContent(folder, file)`: Load from `db-extract/{folder}/{file}`
    - `loadFileContent(owner, repo, path, ref)`: Generic file loading
    - Branch helpers: `ensurePushBranch()` creates `push` from `main` if missing; `loadWordsContentFromPushBranch()`
      returns content + sha; `createWordsContent*`/`updateWordsContent*` write to `push` with optimistic locking (sha
      required for updates)

Usage:

```kotlin
if (GitHubClient.isAvailable()) {
    val content = GitHubClient.loadDbExtractContent("en", "test.json")
}
```

### Word data API and repo updates

- `/word/{lang}/{word}` streams NDJSON (`application/x-ndjson`): base chunk comes from `words` on the `push` branch (
  fallback to `main`, otherwise AI-enhanced from db-extract), optional translated chunk is added when `translations`
  query includes missing target language codes.
- `translations` codes are validated; only languages absent from the current card are processed, and Gemini + db-extract
  data must be available.
- `push` query enqueues Cloud Tasks updates **only when something was processed** (new base card or new translations);
  no-op when nothing changed.
- Client-side `DictionaryClient` filters server responses to requested translation languages, handles online-only lemmas
  by copying raw data before ingesting processed content, and wraps errors in `DictionaryClientException`.
- `/internal/update-repo/{lang}/{word}` pretty-prints JSON, ensures `push` exists, merges with existing via
  `WordDataMerger`, and skips commits when content is identical.
- `WordDataMerger` merges by `sense_id`; existing translations/definitions/examples win, only new language codes/example
  translations (matched by normalized text without `<w>` tags) are appended.

### Server Test Patterns

- Tests use real integrations (no mocking) with `assumeTrue()` for graceful skipping
- Test resources in `server/src/test/resources/`
- Use `@EnabledIf` or `assumeTrue(Client.isAvailable())` for tests requiring credentials
- JUnit 5 with `@ParameterizedTest` for testing multiple providers

## Testing Guidelines

- Do not leave println statements in tests.
    - Prefer descriptive assertion messages (assertTrue, assertEquals, fail with context) to convey failures.
    - If you need temporary debugging during local development, use a debugger or temporary logs and remove them before
      committing.
- Fail fast in tests: do not aggregate errors.
    - Validate items inside loops using immediate assertions; abort the test on the first failure.
    - Avoid collecting errors into lists and failing at the end.

## AI enhancer validation

- `LanguageCardEnhancer` and `TranslationEnhancer` reject unknown `sense_id` values; responses must only reference IDs
  from the source card.
- Translation enrichment adds only missing target languages and needs db-extract data + Gemini; existing
  translations/definitions are left untouched.

## CI notes

- Android emulator workflow caches AVDs by workflow-hash key; cache misses wipe old AVD data. Emulator disk size is
  2048MB and `jlumbroso/free-disk-space` keeps runners lean.

## Key Notes

- Module accessors: :composeApp, :shared, :server
- iOS warnings on non-macOS are expected and harmless
- Downloads are served from the `slovymovy` GCS bucket under the version prefix; `GoogleStorageBucketDataProvider`
  builds URLs and list calls.
- App startup checks `Setting.DATA_VERSION`; when versions diverge it routes to a mismatch screen that deletes all
  downloaded DBs before re-downloading.
- Gradle test service `TestServerService` starts the Ktor server for tests with `IS_TEST`, `SERVER_PORT`, and
  `TEST_DB_DIR`; it kills any existing listener on the port and tails logs at `build/test-server.log`.
