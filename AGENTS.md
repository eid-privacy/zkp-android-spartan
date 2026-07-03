# zkp-android-spartan — Agent Guide

## Project Purpose

`zkp-android-spartan` is an experimental Android application template that aims to demonstrate or host zero-knowledge proof (ZKP) workflows built with [Spartan](https://github.com/microsoft/Spartan)-style proof systems through the [Noir](https://noir-lang.org/) language/toolchain. At this early stage the project is a standard Android app skeleton with an adjacent Rust crate (`noir_spartan`) intended to expose ZKP/Noir utilities to Android via [UniFFI](https://mozilla.github.io/uniffi-rs/).

## Repository Layout

```
zkp-android-spartan/
├── AGENTS.md              # This file
├── README.md              # Human-oriented project overview
├── build.gradle.kts       # Root Gradle build file
├── settings.gradle.kts    # Gradle project settings
├── gradle.properties      # Gradle/Android configuration
├── gradle/libs.versions.toml # Version catalog (AGP 9.0.1, Kotlin 2.0.21, Compose BOM 2024.09.00)
├── app/                   # Android application module
│   ├── build.gradle.kts   # App-level dependencies and build config
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/zkp_android_spartan/
│       │   │   ├── MainActivity.kt          # Jetpack Compose entry point
│       │   │   └── ui/theme/                # Compose Material3 theme files
│       │   └── res/                         # Android resources (not detailed here)
│       ├── test/                            # Unit tests
│       └── androidTest/                     # Instrumented tests
└── noir_spartan/          # Rust crate intended for ZKP/Noir integration
    ├── Cargo.toml         # Rust package manifest (depends on UniFFI 0.31 and noir v1.0.0-beta.22)
    ├── build.rs           # UniFFI scaffolding generation
    └── src/
        └── lib.rs         # Rust library entry point (currently contains a stub `add` function)
```

## Technology Stack

- **Android**: Gradle with Android Gradle Plugin 9.0.1, `compileSdk = 36` (minor API level 1), `minSdk = 24`, `targetSdk = 36`.
- **Language**: Kotlin 2.0.21, Java 11 source/target compatibility.
- **UI**: Jetpack Compose with Material3, Compose BOM `2024.09.00`.
- **Native/Rust**: Rust crate (`noir_spartan`) using `uniffi` to generate bindings for Kotlin/Android.
- **ZKP/Noir**: Depends on the Noir compiler/toolchain (`noir` crate from `v1.0.0-beta.22` branch) and is intended to interface with Spartan-style proving.

## Build Commands

- `./gradlew build` — Build the Android app.
- `./gradlew test` — Run unit tests.
- `./gradlew connectedAndroidTest` — Run instrumented tests (requires a connected device or emulator).
- In `noir_spartan/`:
  - `cargo build` — Build the Rust crate.
  - `cargo test` — Run Rust unit tests.
  - `cargo run --bin uniffi-bindgen generate --library target/debug/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/` (typical UniFFI binding flow; adjust paths/library name as needed).

## Important Notes

- The `noir_spartan` crate is currently a stub: it exposes no useful functions in `noir_spartan.udl` and `lib.rs` only contains a placeholder `add` function. Real ZKP/Noir integration is not yet implemented.
- The Noir dependency points at a large external Git repository; expect long initial build times and ensure the Rust toolchain is installed.
- Android app code is generated from Android Studio's Compose template and does not yet call into the Rust crate.

## License

Mozilla Public License 2.0 (see `LICENSE`).
