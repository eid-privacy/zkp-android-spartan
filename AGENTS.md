# zkp-android-spartan — Agent Guide

## Project Purpose

`zkp-android-spartan` is an experimental Android application template that demonstrates zero-knowledge proof (ZKP) workflows built with a [Spartan2](https://github.com/microsoft/Spartan)-style proof system through the [Noir](https://noir-lang.org/) language/toolchain. The project consists of:

- A standard Android app skeleton (`app/`).
- A Rust crate (`noir_spartan/`) that exposes ZKP utilities to Android via [UniFFI](https://mozilla.github.io/uniffi-rs/).
- A vendored/included Spartan backend and Noir circuits under `app/src/main/assets/spartan-backend/`.

The Rust side is functional (it can prove real Noir circuits), and the Android UI has been updated to a tabbed Compose screen that calls `generate_proof` for each circuit. The UniFFI Kotlin bindings and native `.so` libraries must still be generated from `noir_spartan` before the app can run.

## Repository Layout

```
zkp-android-spartan/
├── AGENTS.md              # This file
├── README.md              # Human-oriented project overview
├── LICENSE                # Mozilla Public License 2.0
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
│       │   ├── assets/spartan-backend/   # Vendored Spartan backend + Noir circuits
│       │   │   ├── spartan-backend/      # Rust crate: R1CS synthesis, proving, verification
│       │   │   ├── circuits/             # Noir circuits (c0000_trivial, c0006_sha256, ...)
│       │   │   ├── preprocessing/        # Off-circuit ECDSA witness precomputations
│       │   │   └── algebra-utils/        # Shared elliptic-curve helpers
│       │   ├── java/com/example/zkp_android_spartan/
│       │   │   ├── MainActivity.kt          # Jetpack Compose entry point (tabbed circuit UI)
│       │   │   ├── AssetUtils.kt            # Copies circuit directories from assets to filesDir
│       │   │   └── ui/theme/                # Compose Material3 theme files
│       │   └── res/                         # Android resources
│       ├── test/                            # Unit tests
│       └── androidTest/                     # Instrumented tests
└── noir_spartan/          # Rust crate exposed to Android
    ├── Cargo.toml         # Rust package manifest (UniFFI 0.31, spartan-backend git dep)
    ├── Cargo.lock         # Resolved dependency graph
    ├── build.rs           # UniFFI scaffolding generation
    └── src/
        ├── lib.rs         # Library entry point, re-exports generate_proof
        └── spartan.rs     # UniFFI-exported generate_proof implementation
```

## Technology Stack

- **Android**: Gradle with Android Gradle Plugin 9.0.1, `compileSdk = 36` (minor API level 1), `minSdk = 24`, `targetSdk = 36`.
- **Language**: Kotlin 2.0.21, Java 11 source/target compatibility.
- **UI**: Jetpack Compose with Material3, Compose BOM `2024.09.00`, Compose Foundation Pager, and Kotlin Coroutines.
- **Native/Rust**: Rust crate (`noir_spartan`) using UniFFI proc-macro exports to generate bindings for Kotlin/Android.
- **ZKP/Noir**: Depends on `spartan-backend` from the `eid-privacy/spartan-backend` repository, which uses a forked Noir compiler (`t256-v0.22-1` tag) and Spartan2.

## Build Commands

### Android

- `./gradlew build` — Build the Android app.
- `./gradlew test` — Run unit tests.
- `./gradlew connectedAndroidTest` — Run instrumented tests (requires a connected device or emulator).

### `noir_spartan` Rust crate

- `cargo build` — Build the Rust crate.
- `cargo test` — Run Rust unit tests.
- `cargo run --bin uniffi-bindgen generate --library ./target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/` — Regenerate Kotlin bindings after API changes.

### Spartan backend and circuits

From `app/src/main/assets/spartan-backend/spartan-backend/`:

- `cargo run --release c0000_trivial` — Prove and verify a specific circuit.
- `cargo run --release` — Prove and verify all known passing circuits.

Set `RUST_LOG=DEBUG` (or `INFO`, `TRACE`) for detailed logs.

### Preprocessing tools

Some circuits need off-circuit ECDSA witnesses. Each tool is a standalone Cargo crate:

```bash
cd app/src/main/assets/spartan-backend/preprocessing/<tool>
cargo run --release
```

See `app/src/main/assets/spartan-backend/preprocessing/README.md` for the tool-to-circuit mapping.

## Important Notes

- The `noir_spartan` crate is no longer a stub: `src/spartan.rs` exports a `generate_proof(pwd: String) -> bool` function. It loads a Noir circuit directory and produces a Spartan2 proof via `spartan-backend`.
- UniFFI bindings are generated from proc-macro attributes (`#[uniffi::export]`).
- The Android app is now a tabbed Compose screen. It discovers the circuit directories under `assets/spartan-backend/circuits/`, copies the selected circuit to `filesDir/circuits/<name>/`, and calls `generate_proof` with that filesystem path.
- The vendored `spartan-backend` is a large Rust codebase that pulls in a forked Noir compiler/toolchain; initial builds can be slow.
- Circuit target artifacts (`circuits/*/target/`) are intentionally versioned in this repository because the generated ACIR bytecode is part of the tracked backend test corpus.
- For circuit compilation from source, use the `nargo-t256` build of the `eid-privacy/noir` fork at tag `t256-v0.22-1`.
- Keep `generate_proof(pwd: String) -> bool` unchanged; richer error reporting was intentionally left out to preserve the existing FFI signature. The Android layer logs full exceptions and relies on the Rust-side `eprintln!` for failure details.

## License

Mozilla Public License 2.0 (see `LICENSE`).
