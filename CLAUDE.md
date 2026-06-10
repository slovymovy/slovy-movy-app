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
- **Data version**: `DataDbManager.VERSION = "v15"`; when bumping, upload DBs under the new prefix in the GCS bucket and
  keep the version in sync. App startup compares this against `Setting.DATA_VERSION`; mismatch routes to
  `DataVersionMismatchScreen`, which deletes all downloaded DBs and re-runs `selectInitialDestination`.
- **Supported languages**: ten total — EN, RU, NL, PL, DE, FR, IT, CS, TR, ES (see
  `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/Language.kt`). Wiktextract source files are only present
  for EN/RU/NL/PL (see `LANG_TO_SOURCE_FILE` in `JsonIngestionBuilder`).

## Code Structure

- **KMP source sets**: commonMain/commonTest for shared code
- **shared module**: cross-platform domain (`data/`, `ingestion/`, `data/learning/`, `data/learning/fsrs`,
  `data/learning/stats`, `data/forms`, `data/favorites`, `data/settings`, `util/`, `api/`); also re-exports the
  vendored FSRS implementation under `external.fsrs`.
- **composeApp module**: Compose UI + app-specific services (`data/remote`, `data/local`,
  `data/learning/intake|session`, `data/export`, `analytics`, `logging`, `speech`, `i18n`, `ui/...`).
- **Compose UI**: composeApp/src/commonMain
- **Android namespace**: com.slovy.slovymovyapp
- **Server main**: com.slovy.slovymovyapp.ApplicationKt
- **buildSrc** ships custom Gradle tasks: `WriteAppVersionTask` / `WriteIosVersionXcconfigTask` (write version
  metadata from git commit count), `VerifyLocalizationKeysTask`, and `TestServerService` (test-time Ktor server).

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
### Learning / Spaced Repetition

The app drives study via an FSRS-backed pipeline. Domain types live in `shared` and are reused by app services in
`composeApp`.

- **Domain types** (`shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/learning/`):
    - `Card` = `id`, `senseId`, `lemmaId`, `langCode`, `family`, `answerKey`, `scheduling`.
    - `CardFamily` (RECOGNIZE_SENSE, PRODUCE_WORD, PRODUCE_WORD_IN_CONTEXT, RECOGNIZE_VOICE) — only PRODUCE_* /
      RECOGNIZE_VOICE families set `testsWordRecall = true`.
    - `CardKind` describes a presentation variant per family (e.g. `SOURCE_DEFINITION_TO_WORD`, `WORD_TO_TRANSLATION`,
      `CLOZE_TRANSLATION`, `LISTENING_TRANSLATION`). Each carries `requiresTranslation`, `isCloze`, `family`, `priority`
      and a `minStability` gate for review-time selection.
    - `CardState`: NEW → LEARNING → REVIEW → RELEARNING. **Stored by ordinal** in the `card` table — keep the order
      stable; any change requires a migration + an update to every state-sensitive SQL query.
    - `Rating` is stored using the FSRS numeric value (1..4) via `ratingFsrsAdapter`, not by ordinal. New ratings
      must extend `fsrsValue` and may not reorder the existing ones.
- **FSRS plumbing** (`shared/.../data/learning/fsrs/`):
    - `FsrsScheduler` wraps `external.fsrs.FSRS`, converts between `CardScheduling` and FSRS `FlashCard`, and produces
      `GradeOutcome` lists for `Again/Hard/Good/Easy`. `apply()` writes back stability/difficulty/due/reps/lapses.
    - `FsrsDefaults.config()` is the single source of truth for tuning constants (weights, retention target,
      cross-family credits, cooldown ratios and floors/caps, exposure gates, daily intake budget). When tuning the
      algorithm, change values here — services pull a `FsrsConfig` instance from `FsrsDefaults` so a single edit
      propagates.
    - Avoid raw day/hour millisecond constants; the config defines durations with `kotlin.time.Duration` helpers.
- **Intake** (`composeApp/.../data/learning/intake/IntakeService.kt`): activates pending favorites by inserting cards
  per `defaultIntakeFamilies`. Honors a daily new-card budget, two pause conditions
  (`pauseIntakeIfQueueAbove`, retention floor over the last 7d), and skips when no variant is buildable. Two run
  modes: `DAILY` (capped by budget) and `CONTINUE_NOW` (capped by `continueNowPendingLemmaLimit`). Logs analytics
  event `LEARNING_INTAKE_RUN` with per-reason skip counters.
- **Session selection & review** (`composeApp/.../data/learning/session/`):
    - `SessionService.nextCard` ranks candidates by `memoryUrgency + overdueBonus - collisionPenalty`. Recent
      same-sense, same-lemma, same-answer, and same-family reviews are penalized to spread practice; same sense within
      the last 3 reviews is hard-excluded.
    - `submitReview` writes the card update and `review_log` row in a single transaction. On Good/Easy/Hard it spaces
      sibling cards (`burySiblingCardsBy{Sense,Lemma,Answer}`) with jittered cooldowns, propagates same-sense credit
      across families (`crossFamilyCredits`), and unlocks the next family once the source card's stability crosses
      `productionUnlockStability` / `contextUnlockStability`.
    - `buildTaskVariants` enumerates the playable `CardVariant`s for a sense + family + translation targets;
      `selectVariantsForReview` applies the `minStability` gate and de-prioritises the last-shown variant.
    - `ExamplePicker` chooses a cloze example, preferring ones the user hasn't seen recently for that sense.
    - `SessionCard.loadState()` distinguishes LOADING / READY / ERROR with explicit `SessionCardLoadErrorReason`s
      (sense missing, translation missing, example missing, etc.) so the UI can show retry vs. skip.
- **Stats** (`shared/.../data/learning/stats/StatsService.kt`): exposes `globalStats`, `reviewQueueStats`,
  `dueNow`, and the screen-shaped `statsScreenData` (streak, monthly practice log, pipeline distribution by stability:
  NEW → FRESH → MIDDLE → STRONG → LEARNED). `retrievability(stabilityDays, elapsedDays)` is reused by the session
  priority function.
- **Wiring**: `App.kt` builds one `IntakeService`, one `SessionService`, and one `StatsService` per session and
  passes them through to ViewModels. `FavoritesReviewCoordinator` debounces full intake runs (5-min cache per
  language) while `refreshDueCountsOnly` re-reads stats without rerunning intake — important on iOS, where intake
  serializes against the visible screen's dictionary queries.

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
- Exception: copy that must render in the *studied* language (not the user's UI locale) does not belong in
  `composeResources/`. `stringResource` resolves by OS locale, so it cannot pick by a per-session language code. For
  these cases, hold the strings in a Kotlin map keyed by `Language` (see
  `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/ui/study/StudyCompletionMessages.kt`) and resolve in the
  ViewModel/state — not in the composable, so random picks are stable across recompositions.

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

### Analytics

- `Analytics` is an `expect object` (per-platform actual). Android wires in `FirebaseAnalyticsLogger`; desktop/iOS
  default to `NoOpAnalyticsLogger` so tests don't crash without an SDK.
- All event names live in the `AnalyticsEvent` enum
  (`composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/analytics/Analytics.kt`). Add new events there so call
  sites stay symbolic; the logger lowercases the enum name when forwarding.
- `Analytics.setUserProperty` is used for stable per-user dimensions: `ui_lang`, `learning_lang`, `data_version`.

### Logging

- `AppLogger` is `expect object` with `debug/info/warn/error(tag, message, throwable)`. Avoid `println` outside tools;
  prefer `AppLogger.warn(TAG, "...", e)` for non-fatal flows (see `FavoriteLemmaRecovery`).

### Data export

- `AppDataExporter` is `expect class` per platform (`androidContext` is only used on Android). Returns
  `AppDataExportResult` carrying an artifact name and an optional share reference.
- Shared logic in `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/data/export/` — `AppDataArchiveWriter`
  emits POSIX tar entries via `TarArchive`. `AppDataSnapshotter` makes consistent SQLite snapshots with
  `VACUUM INTO`, falling back to `wal_checkpoint(FULL)` + file copy when the driver rejects `VACUUM INTO`.
- Snapshots cover `app.db`, `local_dictionary.db`, `local_translation.db` (plus `-wal`/`-shm` sidecars). The
  temp file pattern `*.part` is used for atomic moves.

### Speech / TTS

- `TextToSpeechManager` has platform actuals (Android, iOS, desktop no-op) and emits word-boundary + status callbacks.
- Voice filtering is stored in settings under `Setting.Name.ENABLED_VOICES` (JSON per language). `VoiceFilterHelper`
  loads/saves the enabled IDs and defaults to local (offline) voices when first seen.
- Allow users to open system TTS settings via `openSettings()`; Android uses install/check intents, iOS opens
  Accessibility Speech if allowed.

## Database (SqlDelight)

### Schema Locations

- App DB schema (`appdb`): `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/`
    - Files: `Settings.sq` (key/value JSON store), `Favorites.sq` (`favorites`, `card`, `review_log` tables),
      `WordLists.sq` (`word_list`, `word_list_text`, `word_list_label`, `word_list_sense`, `word_list_meta` —
      relational cache of server-curated word lists plus per-language bundle version).
    - Migrations: `shared/src/commonMain/sqldelight/appdb/com/slovy/slovymovyapp/db/migrations/`
    - Verification DBs: `1.db` … `7.db` alongside the schema files; regenerate the newest one with
      `gradlew :shared:generateCommonMainAppDatabaseSchema` after schema changes
- Dictionary DB schema: `shared/src/commonMain/sqldelight/dictionarydb/com/slovy/slovymovyapp/dictionary/`
    - Includes `lemma`, `lemma_pos`, `lemma_pos_sense_hint` (routes senses to the correct cluster — adapter wired in
      `DatabaseProvider`), `lemma_word_family`, `sense`, `form`, `form_tag`, and per-sense detail tables.
- Translation DB schema: `shared/src/commonMain/sqldelight/translationdb/com/slovy/slovymovyapp/translation/`
- Repository pattern: `SettingsRepository` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/settings/`,
  `FavoritesRepository` in `shared/.../data/favorites/`, `WordListsRepository` in `shared/.../data/lists/`.
  Curated word lists are served from the DB via `ListsService` (`composeApp/.../data/lists/`), which syncs against
  `/lists/{lang}/version` and refetches the bundle only on version mismatch (`ListsClient` is the HTTP layer).
  `Setting.Name` is the canonical list of setting keys
  (LANGUAGE, DICTIONARY, DATA_VERSION, ENABLED_VOICES, VOICE_SETUP_SHOWN, WELCOME_COMPLETED, plus per-screen language
  persistence: SEARCH_LANGUAGE, FAVORITES_LANGUAGE, STATS_LANGUAGE).
- Database bootstrap: `DatabaseProvider` in `shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/db/` —
  configures every column adapter (UUIDs as `BLOB`, enums via `enumOrdinalAdapter()`, FSRS rating via
  `ratingFsrsAdapter()`).
- Platform DB support: expect/actual `PlatformDbSupport` + helpers in
  `composeApp/src/*/kotlin/com/slovy/slovymovyapp/data/remote/`
- Local writable DBs: `local_dictionary.db` and `local_translation.db` via `LocalDbManager` — these survive data
  version bumps and back up online-fetched words.
- Downloaded read-only DBs live in the platform database dir and are cached via `ReadOnlyDatabaseCache`; use the cache
  helpers so drivers get closed when deleting files.
- `DataDbManager` enforces query-only mode for read-only drivers, checks available disk before downloads, writes to
  a `.part` temp file before renaming, and `cleanupCorruptDownloadedDbs()` runs at startup (probes for
  `lemma`/`sense_translation` tables and deletes truncated files so routing sees an accurate has-DB picture).
- `WordFetchManager` reuses in-flight `DictionaryClient.getWord` calls via a `MutableSharedFlow(replay=1)` keyed by
  `(language, lemma, translationsKey, pushToRepo)`. The shared flow keeps fetches alive past ViewModel cancellation so
  the next requester gets the same emissions. Completed entries are removed on the next call.
- `DownloadCoordinator` manages per-key download state (`Idle/Running/Done/Failed/Cancelled`) for setup and
  settings-triggered downloads.
- `FavoriteLemmaRecovery` re-fetches favorite lemmas after a data-version download so locally cached translations stay
  populated; `FavoriteRecoveryController` holds a `ProcessKeepAlive` for the duration of each run so it survives
  backgrounding.
- `NetworkErrorClassifier` translates exceptions into `NetworkError` enums (Offline, Timeout, ServerError(status),
  InsufficientStorage, Unknown). Use it (not raw `e.message`) when surfacing user-visible network errors.

### SqlDelight enum columns

- For columns declared as `INTEGER AS SomeEnum`, keep query parameters typed as the enum whenever possible. Compare or
  assign through the enum-backed column (for example `state = :state` or `state != :new_state`) and pass
  `CardState.NEW`, `CardState.REVIEW`, etc. from Kotlin.
- Do not pass enum ordinals by hand from Kotlin service code. If SqlDelight infers a parameter as `Long` because the
  query compares it to numeric literals, move that state decision into Kotlin and pass the final enum value into SQL.
- Raw enum ordinals are acceptable only in migrations, adapter implementations, or tightly documented legacy data
  handling. Persisted enum order should be covered by a small test when the enum is stored by ordinal.

### Migrations

- Migration files are named `<version>.sqm` (e.g., `1.sqm` to migrate from version 1 to 2)
- Stored in the `migrations/` subdirectory alongside schema files
- Contain SQL statements to upgrade database schema
- Verification `.db` files (e.g., `2.db`, …, `5.db` for appdb) represent the expected schema after each migration step
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
- **Race with fallback**: `Application.kt` defines `raceWithFallback(...)` — starts Gemini immediately and, if it
  fails or doesn't return within `AI_FALLBACK_TIMEOUT_MS` (20s), kicks off OpenAI in parallel and returns whichever
  completes first. Used by both `enhanceWithAI` (base card) and `enhanceWithTranslations` (per target language).
  Cancels the loser. If both providers are unavailable it throws; if only one is configured it bypasses the race.

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
  `WordDataMerger`, and skips commits when content is identical. Authenticated via `CloudTasksAuthVerifier`
  (OIDC Bearer token; audience = the deterministic Cloud Run service URL).
- `WordDataMerger` merges by `sense_id`; existing translations/definitions/examples win, only new language codes/example
  translations (matched by normalized text without `<w>` tags) are appended.
- `POST /feedback` posts to a `Feedback` discussion category on the `slovymovy/slovy-movy-app` repo;
  `POST /feedback/{lang}/{word}` creates a labeled feedback issue on `slovymovy/words`. Both require
  `GitHubClient.isAvailable()`.

### Cloud Tasks / Cloud Run integration

- `CloudRunMetadata` reads service account / numeric project ID / region from the Cloud Run metadata server; the
  deterministic service URL (`https://$K_SERVICE-$projectNumber.$region.run.app`) is used both for queueing tasks and
  for verifying their OIDC audience on the way back in.
- `RepoUpdateTaskClient.queueRepoUpdate(lang, word, json)` enqueues a Cloud Task that POSTs back to
  `/internal/update-repo/{lang}/{word}` with the LanguageCardResponse JSON, signed with an OIDC token from the
  service account. Queue name comes from `CLOUD_TASKS_QUEUE` env (default `repo-updates`).
- A 409 response surfaces as `Conflict` so Cloud Tasks will retry. Other failures are logged but don't fail the
  originating `/word/...` stream.

### Server test mode

- When `IS_TEST=true`, `Application.module()` mounts `Routing.testDataEndpoints()` from `TestApplication.kt`:
  `GET /test/db/list` enumerates DB files in `TEST_DB_DIR` (defaulting to `.test-db-files`) and `GET /test/db/file/{name}`
  serves them. `TestServerDataProvider` in `commonTest` is the client side of this API and replaces
  `GoogleStorageBucketDataProvider` for tests.
- Test mode also mounts `Routing.testListsEndpoints()` and skips the GitHub-backed `/lists/{lang}` routes:
  `PUT /test/lists/{lang}` stages a `LanguageListsResponse` JSON bundle in an in-memory store,
  `DELETE /test/lists/{lang}` removes it, and `GET /lists/{lang}` + `GET /lists/{lang}/version` serve from that store.
  `ListsServiceServerTest` in `commonTest` uses this to exercise the full client → server → DB sync path.

### Server Test Patterns

- Tests use real integrations (no mocking) with `assumeTrue()` for graceful skipping
- Test resources in `server/src/test/resources/`
- Use `@EnabledIf` or `assumeTrue(Client.isAvailable())` for tests requiring credentials
- JUnit 5 with `@ParameterizedTest` for testing multiple providers

## Code Style

- Avoid adding free-standing helper methods (top-level functions or private extensions) in unrelated files. Place new
  methods on the actual class/service that owns the behaviour, in the file where that class is implemented.
- Do not declare extension functions on stdlib/runtime types you don't own (`String`, `Collection`, `List`, `Map`,
  etc.) — a helper like `Collection<String>.filterSelfReferences(...)` reads as general-purpose API while encoding
  class-specific logic. Write a regular function and pass the data as a parameter. Extensions on project-owned or
  generated types (e.g. `DictionaryQueries.resolveRelatedForm`) are fine.
- Avoid default parameter values if possible. Prefer explicit call sites so behaviour is obvious at the call and
  refactors don't silently change semantics for existing callers. Use defaults only when omission has a single,
  obvious meaning that all callers genuinely share.
- For time arithmetic, use `kotlin.time.Duration` helpers (`1.seconds`, `7.days`, `500.milliseconds`) and
  `Instant +/- Duration`; avoid raw millisecond constants like `86_400_000L` or manual multipliers. Convert to epoch
  milliseconds only at API/database boundaries with `toEpochMilliseconds()` or `inWholeMilliseconds`.
- For FSRS / scheduling tuning, edit `FsrsDefaults` constants (and the `FsrsConfig` it produces) rather than
  hard-coding fresh thresholds at the call site. Services receive `FsrsConfig` so a single source of truth keeps
  intake, session selection, cooldowns, and stats in sync.
- Persisted enums: prefer `INTEGER AS Enum` columns with `enumOrdinalAdapter()`; if the enum participates in a
  numeric protocol (e.g. `Rating.fsrsValue`), use a dedicated adapter (`ratingFsrsAdapter`) and keep the protocol
  value stable across versions.

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
  builds URLs and list calls. In tests the bucket is swapped out for `TestServerDataProvider`, which talks to the
  in-process server's `/test/db/*` endpoints.
- Gradle test service `TestServerService` starts the Ktor server for tests with `IS_TEST`, `SERVER_PORT`, and
  `TEST_DB_DIR`; it kills any existing listener on the port and tails logs at `build/test-server.log`.
- Production server URL (`DictionaryClient.PRODUCTION_SERVER_URL`): `https://backend.openwords.ai`.
