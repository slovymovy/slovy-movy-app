Project development guidelines (advanced)

Overview
This repository is a Kotlin Multiplatform (KMP) workspace comprised of:
- composeApp: Compose Multiplatform UI for Android, Desktop, and iOS (framework binaries for iOS).
- shared: KMP shared library used by all frontends and the server.
- server: JVM Ktor server that depends on shared.

The build is driven by the Gradle wrapper (Gradle 8.14.x) with a version catalog (gradle/libs.versions.toml). Kotlin is 2.2.10, Compose Multiplatform is 1.8.2, AGP is 8.11.1, Ktor is 3.2.3.

1) Build / configuration instructions (project‑specific)
Environment prerequisites
- JDK: Use a JDK compatible with Kotlin/JVM target 11. The project configures JVM target 11; Gradle 8.14.x itself supports up to Java 24. JDK 17 LTS is a safe choice for local builds and IDE.
- Android SDK: Required for composeApp Android. compileSdk=36, minSdk=24, targetSdk=36. Ensure local.properties contains sdk.dir=<android-sdk-path>.
- Xcode + iOS toolchain (optional on non‑macOS): iOS targets are configured but can be disabled automatically on non‑macOS. On Windows/Linux, Kotlin/Native iOS targets are disabled during configuration, which is expected and non‑blocking for JVM/Android/Desktop build/test.

Gradle wrapper usage
- Always use the wrapper from repo root: Windows: .\gradlew.bat <task>, macOS/Linux: ./gradlew <task>.
- Type‑safe project accessors are enabled; modules are addressed as :composeApp, :shared, :server, and in Kotlin DSL as projects.composeApp / projects.shared / projects.server.

Key tasks per module
- All tests across modules: .\gradlew.bat test
- Build everything: .\gradlew.bat build
- Shared module (KMP library):
  - Compile/assemble: .\gradlew.bat :shared:build
  - Run tests only for shared: .\gradlew.bat :shared:allTests
- ComposeApp (Android):
  - Assemble debug APK: .\gradlew.bat :composeApp:assembleDebug
  - Install/run on device via IDE. CLI install requires adb and a matching task; AGP 8+ supports installDebug on app modules: .\gradlew.bat :composeApp:installDebug
  - Packaging config excludes META‑INF/AL2.0, LGPL2.1.
  - JVM target for Android code is 11.
- ComposeApp (Desktop):
  - Run desktop app: .\gradlew.bat :composeApp:run
  - Native distributions: .\gradlew.bat :composeApp:packageDistributionForCurrentOS
- ComposeApp (iOS):
  - iOS frameworks are defined (iosX64/iosArm64/iosSimulatorArm64, baseName=ComposeApp, isStatic=true). On macOS, use Xcode with iosApp project to run. On non‑macOS, iOS targets will be marked disabled during Gradle configuration; this is expected.
- Server (Ktor):
  - Run: .\gradlew.bat :server:run (respects -Dio.ktor.development flag from project property 'development')
  - Development mode: .\gradlew.bat -Pdevelopment :server:run

Configuration details that matter
- Version catalog: All key plugin and library versions are in gradle/libs.versions.toml (kotlin, compose, ktor, coroutines, androidX, AGP). Update here and refresh Gradle.
- Kotlin options:
  - Android/Shared modules set Kotlin JVM target 11 via compilerOptions { jvmTarget = JVM_11 } and AGP compileOptions 11.
- iOS on non‑macOS: Expect warnings like "Disabled Kotlin/Native Targets: iosArm64, iosSimulatorArm64, iosX64". This is harmless if you’re not building iOS. To suppress, add kotlin.native.ignoreDisabledTargets=true in gradle.properties.
- Compose Hot Reload plugin is present (org.jetbrains.compose.hot-reload). Use as needed during Android/Desktop development; it’s not required for CI builds.

2) Testing information
Where tests live
- shared module: shared/src/commonTest – multiplatform common tests using kotlin-test.
- composeApp: composeApp/src/commonTest – multiplatform common tests using kotlin-test.
- server: server/src/test – JVM tests using ktor-server-test-host and kotlin-test-junit.

Running tests
- Run all tests in all modules: .\gradlew.bat test
- Shared module only: .\gradlew.bat :shared:allTests
- ComposeApp common tests only: .\gradlew.bat :composeApp:allTests
- Server tests only: .\gradlew.bat :server:test

Filtering tests
- JUnit (server):
  - Single test class: .\gradlew.bat :server:test --tests "com.slovy.slovymovyapp.ApplicationTest"
  - Single test method: .\gradlew.bat :server:test --tests "com.slovy.slovymovyapp.ApplicationTest.someMethod"
- KMP common tests (kotlin-test) run through Gradle’s allTests aggregation per module; IDE filtering is generally easier for commonTest. For CLI granularity, prefer targeting the module and using engine/test filters if configured by the IDE/Gradle.

Adding a new test (example)
- Create a test under shared/src/commonTest/kotlin/... using kotlin-test APIs.
  Example snippet:
  package com.slovy.slovymovyapp
  import kotlin.test.Test
  import kotlin.test.assertTrue
  class GreetingTest {
      @Test fun greet_hasHello() {
          assertTrue(Greeting().greet().startsWith("Hello, "))
      }
  }
- Run the shared tests: .\gradlew.bat :shared:allTests

What we verified
- We executed: .\gradlew.bat test – all modules’ tests passed on Windows with iOS targets disabled (expected). We also added a temporary shared commonTest and ran .\gradlew.bat :shared:allTests successfully, then removed that temporary test to keep the repo clean as per instructions.

3) Additional development information
Code structure and conventions
- KMP source sets: commonMain/commonTest hold shared code/tests (use kotlin-test assertions). Platform-specific code resides under <module>/src/<platform>Main.
- shared module exposes simple APIs (e.g., Greeting.greet()) and common constants (Constants.kt). Prefer adding cross-platform logic here.
- Compose Multiplatform:
  - UI code is in composeApp/src/commonMain. Use compose.runtime/foundation/material3/ui dependencies declared already.
  - Preview tooling is enabled for Android via compose.preview and uiTooling.
  - Desktop entry point is com.slovy.slovymovyapp.MainKt configured via compose.desktop.application.
- Android specifics:
  - Namespace: com.slovy.slovymovyapp, appId matches namespace, min/target/compile SDKs set from version catalog.
  - Signing: Not configured in this sample; add signing configs as needed.
- Server specifics:
  - Application main: com.slovy.slovymovyapp.ApplicationKt, Logback configured (server/src/main/resources/logback.xml).
  - Tests use ktor-server-test-host; prefer writing integration-style tests using test application modules from Ktor 3.x.

Version management / upgrades
- Centralize dependency bumps in gradle/libs.versions.toml. After updates, run .\gradlew.bat build to catch binary incompatibilities.
- Compose compiler plugin uses Kotlin version; keep composeMultiplatform and kotlin versions compatible.

Common pitfalls and tips
- iOS on non‑macOS: The “Disabled Kotlin/Native Targets” warnings are normal; do not attempt to force-enable without a macOS environment.
- When adding new modules or source sets, ensure they’re wired into sourceSets and that test dependencies include kotlin-test.
- For server tests, prefer JUnit 4 style (as configured) or migrate to JUnit 5 by adjusting dependencies and Gradle test configuration.
- For Android builds on CI, ensure Android SDK is provisioned and local.properties is generated or set ANDROID_HOME/ANDROID_SDK_ROOT environment vars.

Useful commands summary
- All tests: .\gradlew.bat test
- Module tests: .\gradlew.bat :shared:allTests | :composeApp:allTests | :server:test
- Build all: .\gradlew.bat build
- Run desktop app: .\gradlew.bat :composeApp:run
- Run server (dev): .\gradlew.bat -Pdevelopment :server:run

Notes for IDEs
- IntelliJ IDEA / Android Studio: Use Gradle import; run configurations for Desktop (:composeApp:run) and Server (:server:run) are auto-created or easy to add. Android run uses standard Android run configuration. For iOS, open iosApp in Xcode and link the KMP framework.

Status of this document
- Commands verified on Windows at 2025-08-25. Tests pass; iOS targets disabled as expected on non‑macOS.
