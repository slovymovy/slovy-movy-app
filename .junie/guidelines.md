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
- Windows: .\gradlew.bat <task>
- macOS/Linux: ./gradlew <task>
- WSL (Windows Subsystem for Linux): cmd.exe /c gradlew.bat <task>
  - WSL Note: Use Windows version of gradle because project files are on C:\ drive (mapped to /mnt/c/ in WSL). File paths in error messages will show Windows paths (C:\...) rather than WSL paths (/mnt/c/...)
- Type‑safe project accessors are enabled; modules are addressed as :composeApp, :shared, :server, and in Kotlin DSL as projects.composeApp / projects.shared / projects.server.

Key tasks per module

Windows:
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

macOS/Linux:
- All tests across modules: ./gradlew test
- Build everything: ./gradlew build
- Shared module (KMP library):
  - Compile/assemble: ./gradlew :shared:build
  - Run tests only for shared: ./gradlew :shared:allTests
- ComposeApp (Android):
  - Assemble debug APK: ./gradlew :composeApp:assembleDebug
  - Install/run on device via IDE. CLI install requires adb and a matching task; AGP 8+ supports installDebug on app modules: ./gradlew :composeApp:installDebug
- ComposeApp (Desktop):
  - Run desktop app: ./gradlew :composeApp:run
  - Native distributions: ./gradlew :composeApp:packageDistributionForCurrentOS
- ComposeApp (iOS, macOS only):
  - iOS frameworks are defined (iosX64/iosArm64/iosSimulatorArm64, baseName=ComposeApp, isStatic=true). Use Xcode with iosApp project to run.
- Server (Ktor):
  - Run: ./gradlew :server:run (respects -Dio.ktor.development flag from project property 'development')
  - Development mode: ./gradlew -Pdevelopment :server:run

WSL (Windows Subsystem for Linux):
Note: Uses Windows gradle wrapper because project files are on C:\ drive (mapped to /mnt/c/). Error messages and test results will reference Windows paths (C:\...) instead of WSL paths (/mnt/c/...).

- All tests across modules: cmd.exe /c gradlew.bat test
- Build everything: cmd.exe /c gradlew.bat build
- Shared module (KMP library):
  - Compile/assemble: cmd.exe /c gradlew.bat :shared:build
  - Run tests only for shared: cmd.exe /c gradlew.bat :shared:allTests
- ComposeApp (Android):
  - Assemble debug APK: cmd.exe /c gradlew.bat :composeApp:assembleDebug
  - Install/run on device via IDE. CLI install requires adb and a matching task; AGP 8+ supports installDebug on app modules: cmd.exe /c gradlew.bat :composeApp:installDebug
- ComposeApp (Desktop):
  - Run desktop app: cmd.exe /c gradlew.bat :composeApp:run
  - Native distributions: cmd.exe /c gradlew.bat :composeApp:packageDistributionForCurrentOS
- Server (Ktor):
  - Run: cmd.exe /c gradlew.bat :server:run (respects -Dio.ktor.development flag from project property 'development')
  - Development mode: cmd.exe /c gradlew.bat -Pdevelopment :server:run

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

Windows:
- Run all tests in all modules: .\gradlew.bat test
- Shared module only: .\gradlew.bat :shared:allTests
- ComposeApp common tests only: .\gradlew.bat :composeApp:allTests
- Server tests only: .\gradlew.bat :server:test

macOS/Linux:
- Run all tests in all modules: ./gradlew test
- Shared module only: ./gradlew :shared:allTests
- ComposeApp common tests only: ./gradlew :composeApp:allTests
- Server tests only: ./gradlew :server:test

WSL:
Note: Test results and compilation errors will show Windows file paths (C:\...) rather than WSL paths (/mnt/c/...).

- Run all tests in all modules: cmd.exe /c gradlew.bat test
- Shared module only: cmd.exe /c gradlew.bat :shared:allTests
- ComposeApp common tests only: cmd.exe /c gradlew.bat :composeApp:allTests
- Server tests only: cmd.exe /c gradlew.bat :server:test

Filtering tests
- JUnit (server):
  - Windows: .\gradlew.bat :server:test --tests "com.slovy.slovymovyapp.ApplicationTest"
  - macOS/Linux: ./gradlew :server:test --tests "com.slovy.slovymovyapp.ApplicationTest"
  - WSL: cmd.exe /c gradlew.bat :server:test --tests "com.slovy.slovymovyapp.ApplicationTest"
  - Single test method (similar pattern for all platforms)
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
- Run the shared tests: 
  - Windows: .\gradlew.bat :shared:allTests
  - macOS/Linux: ./gradlew :shared:allTests
  - WSL: cmd.exe /c gradlew.bat :shared:allTests

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
- Centralize dependency bumps in gradle/libs.versions.toml. After updates, run build command for your platform to catch binary incompatibilities.
- Compose compiler plugin uses Kotlin version; keep composeMultiplatform and kotlin versions compatible.

Common pitfalls and tips
- iOS on non‑macOS: The “Disabled Kotlin/Native Targets” warnings are normal; do not attempt to force-enable without a macOS environment.
- When adding new modules or source sets, ensure they’re wired into sourceSets and that test dependencies include kotlin-test.
- For server tests, prefer JUnit 4 style (as configured) or migrate to JUnit 5 by adjusting dependencies and Gradle test configuration.
- For Android builds on CI, ensure Android SDK is provisioned and local.properties is generated or set ANDROID_HOME/ANDROID_SDK_ROOT environment vars.

Useful commands summary

Windows:
- All tests: .\gradlew.bat test
- Module tests: .\gradlew.bat :shared:allTests | :composeApp:allTests | :server:test
- Build all: .\gradlew.bat build
- Run desktop app: .\gradlew.bat :composeApp:run
- Run server (dev): .\gradlew.bat -Pdevelopment :server:run

macOS/Linux:
- All tests: ./gradlew test
- Module tests: ./gradlew :shared:allTests | :composeApp:allTests | :server:test
- Build all: ./gradlew build
- Run desktop app: ./gradlew :composeApp:run
- Run server (dev): ./gradlew -Pdevelopment :server:run

WSL:
Note: Uses Windows gradle wrapper due to C:\ drive mapping. Output paths will be Windows format (C:\...).

- All tests: cmd.exe /c gradlew.bat test
- Module tests: cmd.exe /c gradlew.bat :shared:allTests | :composeApp:allTests | :server:test
- Build all: cmd.exe /c gradlew.bat build
- Run desktop app: cmd.exe /c gradlew.bat :composeApp:run
- Run server (dev): cmd.exe /c gradlew.bat -Pdevelopment :server:run

Notes for IDEs
- IntelliJ IDEA / Android Studio: Use Gradle import; run configurations for Desktop (:composeApp:run) and Server (:server:run) are auto-created or easy to add. Android run uses standard Android run configuration. For iOS, open iosApp in Xcode and link the KMP framework.

Status of this document
- Commands verified on Windows at 2025-08-25. Tests pass; iOS targets disabled as expected on non‑macOS.
