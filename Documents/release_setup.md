# Cross-Platform Packaging & CI/CD Release Setup

This document describes how to configure the hybrid Kotlin/Rust project for native packaging across Windows, macOS, and Linux, and provides a fully automated CI/CD workflow via GitHub Actions.

---

## 1. Automated Gradle Packaging (`build.gradle.kts`)

We configure Gradle to automatically detect the current host operating system, locate the compiled Rust library, and copy it into the platform-specific resources directory (`src/main/resources/<platform-arch>`) before creating the distribution package.

This layout allows **JNA** to load the matching dynamic library natively at runtime.

### build.gradle.kts Configuration
Overwrite your existing `build.gradle.kts` with the following production-ready configuration:

```kotlin
plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.example.compare"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("net.java.dev.jna:jna:5.14.0")
}

// -------------------------------------------------------------
// Rust Native Library Ingestion Task
// -------------------------------------------------------------
val copyRustLib by tasks.registering(Copy::class) {
    group = "build"
    description = "Copies the compiled Rust library into target resource folders for JNA"

    val osName = System.getProperty("os.name").lowercase()
    val isWin = osName.contains("win")
    val isMac = osName.contains("mac")

    val (srcFile, destDir) = when {
        isWin -> Pair("rust_core/target/release/compare_core.dll", "src/main/resources/win32-x86-64")
        isMac -> {
            val arch = System.getProperty("os.arch").lowercase()
            val folder = if (arch.contains("aarch64") || arch.contains("arm64")) "darwin-aarch64" else "darwin-x86-64"
            Pair("rust_core/target/release/libcompare_core.dylib", "src/main/resources/$folder")
        }
        else -> Pair("rust_core/target/release/libcompare_core.so", "src/main/resources/linux-x86-64")
    }

    from(file(srcFile))
    into(file(destDir))
}

// Bind resources processing to copy the Rust library beforehand
tasks.processResources {
    dependsOn(copyRustLib)
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            // Packaging formats per platform
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,  // Windows installer
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,  // macOS disc image
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb   // Linux Debian package
            )
            packageName = "theCompare"
            packageVersion = "1.0.0"
            
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "68c92a9b-2bbf-412e-9d8f-c020d20dcfb6"
            }
            macOS {
                bundleID = "com.example.compare"
                infoPlist {
                    extraKeys.set(mapOf("NSHighResolutionCapable" to true))
                }
            }
            linux {
                shortcut = true
                menuGroup = "Development"
            }
        }
    }
}
```

---

## 2. CI/CD Release Pipeline (`.github/workflows/release.yml`)

The following workflow compiles the Rust dynamic library and packaging wrapper, runs UniFFI bindings generation, and builds native Compose installers (`.msi`, `.dmg`, `.deb`) in parallel using a GitHub Actions OS matrix.

Create the file `.github/workflows/release.yml` with the following content:

```yaml
name: Cross-Platform Build & Release

on:
  push:
    tags:
      - 'v*' # Trigger build on tags like v1.0.0

permissions:
  contents: write

jobs:
  build-and-release:
    name: Build on ${{ matrix.os }}
    runs-on: ${{ matrix.os }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: windows-latest
            pkg_format: msi
            artifact_path: build/compose/binaries/main/msi/*.msi
            ext: msi
          - os: macos-latest
            pkg_format: dmg
            artifact_path: build/compose/binaries/main/dmg/*.dmg
            ext: dmg
          - os: ubuntu-latest
            pkg_format: deb
            artifact_path: build/compose/binaries/main/deb/*.deb
            ext: deb

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          cache: 'gradle'

      - name: Install Rust Toolchain
        uses: dtolnay/rust-toolchain@stable

      - name: Cache Cargo Dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.cargo/bin/
            ~/.cargo/registry/index/
            ~/.cargo/registry/cache/
            ~/.cargo/git/db/
            rust_core/target/
          key: ${{ runner.os }}-cargo-${{ hashFiles('**/Cargo.toml') }}

      - name: Build Rust Native Core
        run: |
          cd rust_core
          cargo build --release

      - name: Generate UniFFI Bindings
        run: |
          cd rust_core
          cargo run --features=uniffi/cli --bin uniffi-bindgen generate src/lib.rs --language kotlin --out-dir ../src/main/kotlin/bindings

      - name: Setup Permissions (macOS/Linux)
        if: matrix.os != 'windows-latest'
        run: chmod +x gradlew

      - name: Compile and Package Application
        run: ./gradlew package${{ matrix.pkg_format }}

      - name: Upload Build Artifacts to Release
        uses: softprops/action-gh-release@v2
        with:
          files: ${{ matrix.artifact_path }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```
