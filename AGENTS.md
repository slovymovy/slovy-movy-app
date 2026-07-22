# Project development guide

This file describes the repository as it exists today. Treat Gradle configuration, CI, schemas, and the implementation
as the source of truth when they disagree with prose. Keep this guide focused on conventions and invariants that are
easy to miss during a change.

## Repository map

This is a Kotlin Multiplatform workspace with four Gradle modules plus the iOS shell:

| Path / module | Purpose |
| --- | --- |
| `androidApp/` / `:androidApp` | Android application packaging, manifests, Firebase startup, and app-level smoke tests. |
| `composeApp/` / `:composeApp` | Compose Multiplatform UI and app services for Android, desktop, and iOS. It is an Android library, not the APK module. |
| `shared/` / `:shared` | Cross-platform domain models, ingestion, repositories, FSRS logic, and all SqlDelight schemas. |
| `server/` / `:server` | JVM Ktor backend, AI/GitHub/Cloud Tasks integrations, and the database preparation tool. |
| `iosApp/` | Swift/Xcode application shell which embeds the `ComposeApp` framework and installs iOS Firebase integrations. |
| `buildSrc/` | Version-file generation, localization verification, and the test Ktor server used by client tests. |

Important namespaces and entry points:

- Android application ID: `com.slovy.slovymovyapp`; Android app namespace: `com.slovy.slovymovyapp.androidApp`.
- Shared Compose/library namespace: `com.slovy.slovymovyapp`.
- Server main class: `com.slovy.slovymovyapp.ApplicationKt`.
- Shared UI navigation and dependency wiring: `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/App.kt`.
- Versions: `gradle/libs.versions.toml`. The root build derives `versionCode` from the git commit count and
  `versionName` as `1.3.<commit-count>`.

Supported learning languages are EN, RU, NL, PL, DE, FR, IT, CS, TR, and ES; see
`shared/src/commonMain/kotlin/com/slovy/slovymovyapp/data/Language.kt`. Offline Wiktextract source mappings currently
exist only for EN, RU, NL, and PL.

## Environment and Gradle commands

- Use Java 21. CI and production images use it; Java 25+ can expose Kotlin/Gradle compatibility problems.
- On Unix, run the checked-in wrapper as `./gradlew`.
- In native Windows terminals use `gradlew.bat` (`./gradlew.bat` from Git Bash). From WSL when using the Windows
  toolchain, use `cmd.exe /c gradlew.bat`.
- Android SDK levels are compile 37, target 36, and min 24. JVM bytecode for Android/shared targets is 11.
- iOS targets are enabled only on macOS. Skipped/disabled iOS-target messages elsewhere are expected.

Common development commands:

```text
./gradlew build
./gradlew :composeApp:run
./gradlew -Pdevelopment :server:run
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:packageDistributionForCurrentOS
```

Use targeted tests while iterating:

```text
./gradlew :shared:allTests
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:desktopTest
./gradlew :androidApp:testMinifiedTestUnitTest
./gradlew :server:test
```

Useful broader or platform-specific checks:

```text
./gradlew :composeApp:allTests
./gradlew :composeApp:connectedAndroidDeviceTest :androidApp:connectedAndroidTest
./gradlew :androidApp:connectedMinifiedAndroidTest
./gradlew iosSimulatorArm64Test                         # macOS only
./gradlew :androidApp:checkReleaseOptimizedBuild
./gradlew :composeApp:verifyLocalizationKeys
./gradlew :shared:verifyCommonMainAppDatabaseMigration
```

There is no single portable `test` command that covers every KMP and Android variant. CI deliberately runs these
separately: localization parity, shared tests, Compose Android host tests, minified Android app unit tests, desktop
tests, server tests, connected Android tests, and iOS simulator tests on macOS. Mirror the relevant CI jobs for the
area changed.

The `minifiedTest` Android build mirrors release shrinking/obfuscation but uses debug signing and an ID suffix. Prefer
its unit/smoke checks for changes that could be affected by R8. `checkReleaseOptimizedBuild` combines the minified unit
tests with release assembly.

## Test layout and conventions

Most cross-platform behavior tests live in `composeApp/src/commonTest`, including tests for code owned by `shared`.
`BaseTest` and `TestContext` provide platform-specific databases and filesystem/context wiring:

- `androidHostTest`: Robolectric host tests; run with `:composeApp:testAndroidHostTest`.
- `androidDeviceTest`: instrumented tests; run with `:composeApp:connectedAndroidDeviceTest`.
- `desktopTest`: JDBC-backed tests; run with `:composeApp:desktopTest`.
- `iosTest`: native tests, reached by the simulator task on macOS.
- `composeApp/src/androidTest` contains Android support code shared by the host and device test compilations.

Default new KMP coverage to `composeApp/src/commonTest` and extend `BaseTest`. Put setup differences in the relevant
`TestContext.<platform>.kt` actual or another small expect/actual helper. Add a platform-only test only when the
behavior really belongs to that platform.

`androidApp/src/androidTest` contains full application/package smoke tests. These complement, rather than replace, the
Compose library's device tests.

Server tests live in `server/src/test` and use JUnit 5. They are a mixture of local unit/integration tests and live
integration tests; do not assume every credential-dependent test skips. In particular, the GitHub client test expects
`ACCESS_TO_GH_TOKEN` or a local GitHub key. AI tests tolerate unavailable providers where their test explicitly checks
availability. Preserve those distinctions when adding tests.

Client database/list tests use `TestServerService` from `buildSrc`. It starts the Ktor server with `IS_TEST`,
`SERVER_PORT`, `TEST_DB_DIR`, and `TEST_LISTS_DIR`, injects the selected port into tests, and writes logs to
`build/test-server.log`. It may terminate an old listener occupying its configured test port. Test DB/list fixtures
default to `.test-db-files` and `.test-lists-files`.

### Known local failures without a GitHub token

The word-fetch cases in `DictionaryClientTest` drive the test server's `/word/{lang}/{word}` route, which reads base
data from the `slovymovy/words` repository. Without `ACCESS_TO_GH_TOKEN` (or a local GitHub key file) the server
answers `503 GitHub token not configured` and these five fail in both `:composeApp:testAndroidHostTest` and
`:composeApp:desktopTest`:

- `getWord_fetchesFromServer_whenOnlineOnlyWithoutTranslations`
- `getWord_fetchesBothWordAndTranslations_whenOnlineOnlyWithTranslations`
- `getWord_fetchesTranslations_whenWordExistsButTranslationsMissing`
- `getWord_fetchesTranslations_ru_to_german`
- `getWord_fetchesTranslations_ru_to_english_and_german`

Treat exactly this set, with exactly that 503, as an environment gap rather than a regression: note it and move on
instead of investigating. Any other failing test, or one of these failing for a different reason, is a real failure.
CI supplies the token, so they must pass there.

Testing rules:

- Do not leave `println` calls in tests. Use descriptive assertion messages.
- Fail at the first bad item instead of accumulating a list of failures.
- Keep test data deterministic. Credential/network tests must make their requirements explicit.
- Run the narrowest relevant tests first, then the CI-equivalent checks for the changed area.

## Generated code and build-owned files

- Do not edit `composeApp/build/generated/sources/valkyrie/`; it is generated from SVG resources.
- `WriteAppVersionTask` generates Kotlin version metadata. `WriteIosVersionXcconfigTask` generates the Xcode version
  include. Both derive their values from git history.
- SqlDelight generates query/database types from `shared/src/commonMain/sqldelight`; change `.sq`/`.sqm` sources, not
  generated Kotlin.
- Gradle build/cache directories and platform build products are not source files and should not be committed.

## Compose UI and navigation

Use a thin stateful screen entry point and a stateless rendering composable:

```kotlin
data class ScreenUiState(...)

class ScreenViewModel(...) : ViewModel() {
    var state by mutableStateOf(ScreenUiState(...))
        private set
    val scrollState = LazyListState()
}

@Composable
fun Screen(viewModel: ScreenViewModel) {
    ScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onAction = viewModel::onAction,
    )
}
```

- Put durable mutable screen state, loading/error flags, dialog visibility, and scroll state in the ViewModel/`UiState`
  so they survive recomposition and configuration changes. `remember` is still appropriate for derived/render-only
  state whose lifetime is intentionally local; do not use it to hide screen business state.
- Give `*Content` explicit callbacks and enough state to render without runtime services. Keep defaults only when their
  omitted meaning is truly unambiguous, such as a fresh preview scroll state.
- Map domain objects to UI models outside the rendering composable when the mapping contains decisions.
- Add previews for meaningful content/loading/error/empty states.

`App.kt` uses typed destinations (`AppDestination`) with Compose Navigation. Screen ViewModels are normally created
with `viewModel(viewModelStoreOwner = backStackEntry) { ... }` so their lifetime matches the navigation entry. The
current intentional exceptions are app-scoped Favorites/Settings ViewModels and the bounded Word Details ViewModel
cache. Do not accidentally change those lifetimes when refactoring navigation.

Every preview must render light and dark themes:

```kotlin
@Preview
@Composable
private fun ScreenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        ScreenContent(...)
    }
}
```

Import `PreviewParameter` from `androidx.compose.ui.tooling.preview.PreviewParameter`. `ThemePreviewProvider` and
`ThemedPreview` are in `composeApp/src/commonMain/kotlin/com/slovy/slovymovyapp/ui/Preview.kt`.

## Localization

Compose resources live under `composeApp/src/commonMain/composeResources`. Base UI strings are in `values/strings.xml`;
localized UI resource sets currently exist for DE, ES, IT, NL, PL, and RU. That is intentionally a smaller set than
the ten learning languages.

- Use `stringResource(Res.string.<key>)` for composable UI and accessibility text. Use XML placeholders and `<plurals>`
  rather than building grammar in Kotlin.
- Keep user-visible `commonMain` UI text out of Kotlin literals. Preview-only literals are acceptable.
- `stringResource` follows the OS/UI locale. Copy that must be in the studied language belongs in a `Language`-keyed
  model resolved by the ViewModel, as in `StudyCompletionMessages.kt`.
- For text that must be represented before composition, use the `UiText` pattern in `composeApp/.../i18n` or pass the
  localized value down from the UI boundary.
- Platform-owned surfaces need platform resources: Android notification/service strings are under
  `composeApp/src/androidMain/res/values*`; iOS metadata/permission text belongs in native localization files.
- Run `./gradlew :composeApp:verifyLocalizationKeys`. It checks every localized Compose resource set against the base,
  including plural names and quantities.

## Icons and images

Valkyrie converts bundled SVGs from `composeApp/src/commonMain/valkyrieResources/` into `SlovyIcons` extension
properties in package `com.slovy.slovymovyapp.ui.icons`. Filenames must not contain spaces. Generation runs before
Kotlin compilation.

```kotlin
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.icons.MyIcon

SlovyIcons.MyIcon
```

Use `Icon` for monochrome vectors that should receive a tint and `Image` for multicolor illustrations. `EmptyState`
has both an `ImageVector` overload and an `iconContent` overload for that reason. Server-delivered curated-list SVGs
cannot use Valkyrie; `WordListIcon` renders them through the app's singleton Coil 3 loader with SVG support.

## Application services and platform wiring

`App.kt` creates the app/session-level repositories and services, including dictionary/list clients, local/downloaded
database managers, `WordFetchManager`, lemma recovery, intake/session/stats services, downloads, TTS, and export. Keep
expensive managers stable across recompositions; do not recreate caches or coordinators in screen content.

Platform services use expect/actual or injectable interfaces:

- `Analytics` defaults to a no-op logger, but Android `MainActivity` and the iOS Swift shell install Firebase Analytics.
  Event names come from `AnalyticsEvent` and are lowercased by the Firebase logger. Stable user properties are
  `ui_lang`, `learning_lang`, and `data_version`.
- `PerformanceMonitoring` is also Firebase-backed on Android/iOS and no-op for desktop/tests.
- `AppLogger` writes to the platform console and a bounded developer log buffer. Android/iOS install a Crashlytics
  sink for warnings/errors; desktop uses console output. Use lazy `debug(tag, throwable) { ... }` and the structured
  `info`/`warn`/`error` methods instead of `println`.
- `TextToSpeechManager` has Android and iOS implementations and a desktop no-op. It reports `IDLE`/`SPEAKING` status;
  it does not expose word-boundary callbacks. Voice selection is stored as per-language JSON in `ENABLED_VOICES`.
  `VoiceFilterHelper` honors each platform voice's `enabledByDefault` flag and otherwise favors offline voices.

## Database architecture

All schemas are in `shared/src/commonMain/sqldelight`:

- `appdb`: writable `app.db`. `Settings.sq`, `Favorites.sq`, and `WordLists.sq` define settings, favorites/cards/review
  logs, and the curated-list cache. Repository entry points are `SettingsRepository`, `FavoritesRepository`, and
  `WordListsRepository`.
- `dictionarydb`: lemma/POS/sense/form tables and `lemma_pos_sense_hint` routing.
- `translationdb`: per-sense translations.

`DatabaseProvider` is the central adapter wiring. UUIDs are stored as BLOBs. Ordinal enums use
`enumOrdinalAdapter()`. FSRS `Rating` uses `ratingFsrsAdapter()` because its persisted values are the FSRS protocol
numbers 1..4, not Kotlin ordinals.

There are three distinct database lifecycles:

1. `app.db` is the persistent app/state database.
2. `local_dictionary.db` and `local_translation.db` are writable caches for online-fetched content.
3. Versioned dictionary/translation DBs are downloaded read-only assets managed by `DataDbManager`.

Do not assume the writable local caches survive a data-version reset. The data-version mismatch flow closes and deletes
both downloaded DBs and local dictionary/translation caches before rerunning initial routing. `LemmaRecovery` then
repopulates missing content for favorites and curated-list senses. Recovery items are deduplicated by language and
normalized lemma, and the controller keeps recovery alive with platform process-keepalive support.

`DataDbManager.VERSION` is currently `v15`. Downloaded DB URLs are under that prefix in the `slovymovy` GCS bucket.
Startup removes `.part`, undersized, or schema-invalid downloads before routing. Downloads check disk space, use a
`.part` file plus atomic move, validate the expected schema, and only then record the installed data version.

Read-only and local database managers coordinate active readers with leases/locks. Use their public suspending
`withDictionary...` / `withTranslation...` helpers so deletion waits for readers and drivers are closed safely,
especially on iOS. Application code must not retain a query/driver beyond the helper block or bypass the manager with
a direct driver; direct opens are reserved for manager internals and controlled test setup.

### Schema and migration changes

Migrations are named `<version>.sqm`, where `N.sqm` upgrades schema N to N+1. Current verification snapshots are:

- app DB: `1.db` through `9.db`, with migrations `1.sqm` through `8.sqm`;
- dictionary DB: `1.db` through `5.db`, with migrations `1.sqm` through `4.sqm`;
- translation DB: `1.db` through `2.db`, with migration `1.sqm`.

After an app schema change, add the migration, regenerate the newest schema snapshot, and verify it:

```text
./gradlew :shared:generateCommonMainAppDatabaseSchema
./gradlew :shared:verifyCommonMainAppDatabaseMigration
```

Migration verification is disabled on native Windows because of SqlDelight issue #5312. It runs on supported
non-Windows hosts.

For `INTEGER AS SomeEnum` columns, keep query parameters enum-typed and pass the enum from Kotlin. Never hand an
ordinal from service code to a query. Raw ordinals belong only in migrations/adapters or explicitly documented legacy
handling.

SQLite versions on Android 11 and older cap a statement at 999 bound variables. For an unbounded SqlDelight `IN ?`
query, use `queryInChunks` from `shared/.../util/SqlInChunks.kt`; its 500-item chunks leave room for other parameters.
It deduplicates inputs and concatenates chunk results, so callers that need a global ordering must sort the combined
result themselves.

Persisted enum/protocol invariants:

- `CardState` order is `NEW`, `LEARNING`, `REVIEW`, `RELEARNING`. Reordering or inserting values changes stored data
  and requires a migration plus review of every state-sensitive query.
- `Rating.fsrsValue` is 1..4 for Again/Hard/Good/Easy. Preserve existing values and use its dedicated adapter.
- Other ordinal-backed enums require the same care; add a small persistence-order test when introducing one.

## Dictionary ingestion and fetching

`JsonIngestionBuilder` is intentionally strict and deterministic:

- Lemma IDs are MD5-derived from the lemma plus its normalized form. Raw input IDs supply sense and lemma-POS cluster
  identity; malformed/incomplete UUID strings are padded by the parser for resilience.
- Every lemma must have Zipf frequency data. Duplicate sense IDs across raw entries are errors.
- Native raw entries drive POS/form clustering. Equivalent native form sets are merged; other entries are assigned to
  the best matching cluster, and `lemma_pos_sense_hint` records the resulting sense route.
- Forms are deduplicated by form, normalized form, tags, and source; do not silently collapse source-specific forms.
- Raw-only online lemmas can be ingested first, processed data can later overlay them, and translations-only ingestion
  is supported only after the base senses exist.

Build or rebuild downloadable DB files with the server CLI task:

```text
./gradlew :server:runDbPrepTool --args='--db-extract <path> --processed <path> --out <path> --freq <path>'
```

Append `--test` inside the quoted arguments for a deterministic subset of 500 words per language.

`DictionaryClient` streams and ingests base content before translated content. If an online result depends on raw rows
from an installed DB, it copies those rows into the writable local DB before applying processed data. It filters server
translations to the requested target languages and wraps transport/protocol errors in `DictionaryClientException`.

`WordFetchManager` shares in-flight work by normalized `(language, lemma, translations, push)` key using replaying
flows, so requests survive an individual ViewModel cancellation and the next requester observes the same emissions.
Completed entries are removed on the next call. Preserve cancellation semantics: rethrow `CancellationException`
instead of classifying or logging it as a failure.

Use `NetworkErrorClassifier` to turn failures into `Offline`, `Timeout`, `ServerError`, `InsufficientStorage`, or
`Unknown`; do not surface raw exception messages as user-facing copy. `DownloadCoordinator` is the source of truth for
per-key `Idle`/`Running`/`Done`/`Failed`/`Cancelled` download state.

## Learning and spaced repetition

The shared learning domain is under `shared/.../data/learning`; app intake/session orchestration is under
`composeApp/.../data/learning`.

- `Card` identifies a sense/lemma/language, a `CardFamily`, an answer key, and scheduling data.
- Families are `RECOGNIZE_SENSE`, `PRODUCE_WORD`, `PRODUCE_WORD_IN_CONTEXT`, and `RECOGNIZE_VOICE`. Every family except
  `RECOGNIZE_SENSE` tests word recall.
- `CardKind` is a playable presentation variant with translation/cloze requirements, family, priority, and a minimum
  stability gate.
- `FsrsScheduler` converts between app scheduling data and the vendored `external.fsrs` implementation and produces
  outcomes for Again/Hard/Good/Easy.
- `FsrsDefaults.config()` owns scheduling, cross-family credit, cooldown, unlocking, exposure, and intake tuning.
  Change those policies there rather than adding a threshold at a call site. Stats pipeline display thresholds are a
  separate concern currently owned by `StatsService`.

`IntakeService` activates pending favorites within the daily task-family budget. It applies queue and seven-day
retention pause rules and has `DAILY` and explicitly bounded `CONTINUE_NOW` modes. It skips families with no buildable
variant and reports performance plus `LEARNING_INTAKE_RUN` analytics with per-reason counts.

`SessionService.nextCard` ranks candidates by memory urgency plus overdue bonus minus collision penalties. A sense seen
within the last three reviews is excluded; recent same-sense/lemma/answer/family work is otherwise penalized. Review
submission updates the card and review log in one transaction. Non-Again answers bury siblings; eligible successful
answers also apply same-sense cross-family credit and unlock the next family once configured stability gates are met.

`buildTaskVariants` enumerates valid `CardVariant`s and review selection applies `minStability` while de-prioritizing
the last-shown kind. `ExamplePicker` favors examples that were not seen recently. `SessionCard.loadState()` exposes
explicit `LOADING`/`READY`/`ERROR` states and load-error reasons so UI can distinguish retry from skip.

`StatsService` owns queue/global/screen snapshots, streak/practice data, and the pipeline stages `QUEUE`, `NEW`,
`FRESH`, `MIDDLE`, `STRONG`, and `LEARNED`. Its retrievability function is reused in session priority.
`FavoritesReviewCoordinator` caches a full intake refresh for five minutes per language; `refreshDueCountsOnly` must
not rerun intake. It also avoids intake during a data-version mismatch.

## Curated word lists

Production list data comes from `lists/{lang}/` in the `slovymovy/words` repository. Each list is `{id}.json` with an
optional `{id}.svg`; explicit JSON icon data is the fallback. `ListsService` compares `/lists/{lang}/version` and only
replaces the app DB bundle on a version mismatch. The repository serves cached DB data without requiring the network.

The server version is derived from the Git tree and cached/coalesced. Feed order is ascending explicit `order`, with
missing order last and ID as the final tie-break. Keep an English base text for list metadata. Legacy `senseIds` can be
decoded, but entries without lemma text cannot participate in missing-lemma recovery; new data should use the current
sense objects.

In `IS_TEST=true` mode, `PUT`/`DELETE /test/lists/{lang}` manage an in-memory bundle. Unstaged languages fall back to
`TEST_LISTS_DIR/{lang}` using the same JSON/SVG layout. `/lists/{lang}` and `/lists/{lang}/version` serve either source.

## Server APIs and external integrations

Credentials are resolved from environment first, then ignored local files:

| Service | Environment | Local fallback |
| --- | --- | --- |
| OpenAI | `OPENAI_API_KEY` | `.openai_api_key` |
| Gemini | `AISTUDIO_KEY` | `.aistudio_key` |
| GitHub | `ACCESS_TO_GH_TOKEN` | `server/.github_key`, then `.github_key` |

Fallback paths are relative to the server process working directory. Never commit key files.

AI providers implement the common `AIProvider` contract with caching/retry support. `raceWithFallback` starts Gemini
first, starts OpenAI after `AI_FALLBACK_TIMEOUT_MS` or a Gemini failure, returns the first success, and cancels the
loser. If only one provider is configured it uses that provider directly. Base and translation enhancement require
db-extract source data; translation enhancement can use either configured provider and adds only missing target
languages. Both enhancers reject response sense IDs absent from the source card.

Important routes and data flow:

- `GET /word/{lang}/{word}` returns NDJSON: a base chunk, then an optional translated chunk. Base data comes from the
  words repo `push` branch, then `main`, then AI enhancement of db-extract. Requested translation codes are validated,
  self/available translations are filtered, and only missing languages are processed.
- The `push` query queues a repo update only when base or translation processing changed something.
- `GET /lists/{lang}` and `/lists/{lang}/version` serve curated list bundles.
- `POST /feedback` creates a Feedback discussion in `slovymovy/slovy-movy-app`.
- `POST /feedback/{lang}/{word}` and `POST /list-suggestion/{lang}` create labeled issues in `slovymovy/words`.
- `POST /internal/update-repo/{lang}/{word}` is the authenticated Cloud Tasks callback.

`GitHubClient` handles large `encoding=none` files through `downloadUrl`, ensures a `push` branch from `main`, and uses
SHA-based optimistic locking for updates. `WordDataMerger` merges by `sense_id`: existing definitions/translations/
examples win, while missing languages and normalized example translations are appended. Identical merged content does
not create a commit; a 409 conflict remains retryable.

`RepoUpdateTaskClient` posts the processed JSON back to the deterministic Cloud Run service URL using a service-account
OIDC token. `CloudTasksAuthVerifier` verifies the same URL as audience. Queue name is `CLOUD_TASKS_QUEUE`, defaulting to
`repo-updates`. Task-queue failures are logged without failing the originating word stream.

With `IS_TEST=true`, the server mounts test-only DB routes (`/test/db/list`, `/test/db/file/{name}`) and list routes,
using `TEST_DB_DIR`/`TEST_LISTS_DIR`. Production list routes backed by GitHub are replaced by the test list source.

Production client server URL is `https://backend.openwords.ai`.

## Export, settings, and data-version behavior

`AppDataExporter` is expect/actual; shared tar/snapshot logic is in `composeApp/.../data/export` and
`composeApp/.../data/remote/AppDataSnapshotter.kt`. A consistent snapshot uses `VACUUM INTO`, with
`wal_checkpoint(FULL)` plus copy as fallback. The archive contains the three main snapshots (`app.db`,
`local_dictionary.db`, `local_translation.db`), not live `-wal`/`-shm` sidecars. `.part` names are used for atomic
file destinations where the platform permits.

`Setting.Name` is the canonical key list. It includes onboarding/language/data/voice settings, per-screen persisted
languages, developer mode, and migration markers. Add keys there and use `SettingsRepository`; do not invent raw
string keys at call sites.

When changing `DataDbManager.VERSION`, upload every required dictionary/translation DB under the new GCS prefix and
keep app routing/recovery behavior in mind. Startup compares the installed `Setting.DATA_VERSION`; a mismatch is an
intentional destructive cache refresh, followed by initial-destination selection and recovery.

## Code style and review checklist

- Put behavior on the class/service that owns it. Avoid unrelated top-level helpers and extensions on stdlib/runtime
  types (`String`, `List`, `Map`, and so on). Extensions on project-owned/generated types are fine.
- Prefer explicit call sites over default parameter values. Use a default only when omission has one stable, obvious
  meaning for every caller.
- Use `kotlin.time.Duration` (`500.milliseconds`, `7.days`) and `Instant +/- Duration`; convert to epoch milliseconds
  only at storage/API boundaries.
- In coroutine catch blocks, rethrow `CancellationException` before retrying, logging, or mapping errors.
- Keep FSRS policy in `FsrsDefaults`, DB adapters in `DatabaseProvider`, settings keys in `Setting.Name`, analytics
  names in `AnalyticsEvent`, and user-facing copy in localization resources or the studied-language model.
- Use `AppLogger` for diagnostics and `NetworkErrorClassifier` for user-visible network categories.
- Preserve unrelated working-tree changes. Never edit generated output to work around a source/configuration problem.

Before considering a change complete, check the relevant subset of:

1. Common behavior has a `composeApp/src/commonTest` test when it can run cross-platform.
2. Persisted enum/schema changes include migration and adapter/query review.
3. UI states have stateless content, explicit callbacks, and themed previews.
4. New UI/accessibility copy is localized and localization parity passes.
5. Cancellation is not swallowed and database leases do not escape their block.
6. Targeted tests pass, followed by the CI-equivalent task(s) for the affected module/platform.
