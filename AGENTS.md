# zkp-android-spartan — Agent Guide

## Project Purpose

`zkp-android-spartan` is an experimental Android application template that demonstrates zero-knowledge proof (ZKP) workflows built with a [Spartan2](https://github.com/microsoft/Spartan)-style proof system through the [Noir](https://noir-lang.org/) language/toolchain. The project consists of:

- A standard Android app skeleton (`app/`).
- A Rust crate (`noir_spartan/`) that exposes ZKP utilities to Android via [UniFFI](https://mozilla.github.io/uniffi-rs/).
- A vendored/included Spartan backend and Noir circuits under `app/src/main/assets/spartan-backend/`.

The Rust side is functional (it can prove real Noir circuits), and the Android UI is a tabbed Compose screen that calls `generate_proof` for each circuit. The UniFFI Kotlin bindings are already checked in under `app/src/main/java/com/example/zkp_android_spartan/uniffi/`, and the native `.so` libraries are produced by `cargo ndk` during the Android build.

## Repository Layout

```
zkp-android-spartan/
├── AGENTS.md              # This file
├── README.md              # Human-oriented project overview
├── LICENSE                # Mozilla Public License 2.0
├── build.gradle.kts       # Root Gradle build file
├── settings.gradle.kts    # Gradle project settings
├── gradle.properties      # Gradle/Android configuration
├── gradle/libs.versions.toml # Version catalog (AGP 9.3.1, Kotlin 2.2.10, Compose BOM 2024.09.00)
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
│       │   │   ├── AssetUtils.kt            # Copies circuit directories from assets to filesDir, with a circuit-specific helper that ensures target/*.gz artifacts are present
│       │   │   └── ui/theme/                # Compose Material3 theme files
│       │   └── res/                         # Android resources
│       ├── test/                            # Unit tests
│       └── androidTest/                     # Instrumented tests
└── noir_spartan/          # Rust crate exposed to Android
    ├── Cargo.toml         # Rust package manifest (UniFFI 0.31, spartan-backend git dep)
    ├── Cargo.lock         # Resolved dependency graph
    ├── uniffi.toml        # UniFFI binding configuration (Kotlin package / cdylib name)
    ├── src/
    │   ├── lib.rs         # Library entry point, re-exports generate_proof
    │   └── spartan.rs     # UniFFI-exported generate_proof implementation
    └── src/bin/
        └── uniffi-bindgen.rs  # CLI binary for regenerating Kotlin bindings
```

## Technology Stack

- **Android**: Gradle with Android Gradle Plugin 9.3.1, `compileSdk = 37`, `minSdk = 24`, `targetSdk = 36`.
- **Language**: Kotlin 2.2.10, Java 11 source/target compatibility.
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
- `cargo run --bin uniffi-bindgen generate --library ./target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/` — Regenerate Kotlin bindings after API changes. This writes the file to `app/src/main/java/com/example/zkp_android_spartan/uniffi/noir_spartan/noir_spartan.kt`.

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

## Devbox and Emulator Workflow

The toolchain (Rust, JDK 17, Android platform tools, gcc/cmake) is pinned per-project with **devbox + nix** rather than global installs, via `devbox.json` at the repo root. Every command below runs inside the devbox environment (prefix with `devbox run`, or open a shell with `devbox shell`). The `init_hook` sets `ANDROID_HOME=$HOME/Android`, `JAVA_HOME`, `ANDROID_NDK_HOME`, puts `~/.cargo/bin` on `PATH`, runs `rustup default stable`, and exports `CARGO_NET_GIT_FETCH_WITH_CLI=true` (cargo's built-in libgit2 cannot authenticate to the `ssh://git@github.com` dependencies, so it must shell out to the git CLI).

Prerequisites once per clone: `git submodule update --init` (pulls the `spartan-backend` assets), `devbox install`, and `rustup target add x86_64-linux-android aarch64-linux-android` plus `cargo install cargo-ndk`. Ensure `local.properties` has `sdk.dir=/home/ubuntu/Android` (the complete SDK; `/opt/android` is a stub).

Devbox scripts (`devbox run <name>`):

```bash
devbox run rust_build         # cargo build --release in noir_spartan (host libnoir_spartan.so)
devbox run gen_bindings       # regenerate the UniFFI Kotlin bindings (uses --no-format; ktlint is not installed)
devbox run ndk_build          # cargo ndk build .so for arm64-v8a + x86_64 into app/src/main/jniLibs
devbox run build_apk          # ./gradlew :app:assembleDebug
devbox run install_apk        # adb install -r the debug APK
devbox run launch_app         # adb am start the MainActivity
devbox run ensure_avd         # create the Pixel_9 AVD if missing (scripts/ensure_avd.sh)
devbox run emulator           # ensure_avd, then start Pixel_9 windowed
devbox run emulator-headless  # ensure_avd, then start Pixel_9 with -no-window -no-audio -gpu swiftshader_indirect
```

The generated bindings (`app/src/main/java/.../uniffi/`) and native libs (`app/src/main/jniLibs/`) are **git-ignored**, so a fresh clone must run `gen_bindings` and `ndk_build` before the app will compile and run. The emulator is x86_64, so `ndk_build`'s x86_64 output is what gets exercised on it; the arm64-v8a slice is for physical devices.

End-to-end run on the emulator:

```bash
devbox run emulator-headless          # wait for boot: adb wait-for-device; getprop sys.boot_completed == 1
devbox run rust_build                 # first build pulls the forked Noir toolchain and is slow (~10 min)
devbox run gen_bindings
devbox run ndk_build
devbox run build_apk
devbox run install_apk
devbox run launch_app
adb exec-out screencap -p > shot.png  # then tap "Generate proof" with: adb shell input tap <x> <y>
```

Two host-specific build workarounds:

- **Gradle daemon JVM crash.** `gradle/gradle-daemon-jvm.properties` pins the daemon to Java 21, so Gradle auto-provisions Temurin 21, which SIGSEGV-crashes during R8 dexing on this machine (leaving `hs_err_pid*.log`). Work around it by running the daemon in interpreter mode for the build only: temporarily set `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8 -Xint` in `gradle.properties`, build, then revert. Do not commit `-Xint`.
- **cargo SSH fetch.** As noted above, `CARGO_NET_GIT_FETCH_WITH_CLI=true` (set by the devbox `env`) is required for the `spartan-backend`/Noir git dependencies to resolve.

## Important Notes

- The `noir_spartan` crate is no longer a stub: `src/spartan.rs` exports a `generate_proof(pwd: String) -> bool` function. It loads a Noir circuit directory and produces a Spartan2 proof via `spartan-backend`.
- UniFFI bindings are generated from proc-macro attributes (`#[uniffi::export]`). The Kotlin binding file is committed at `app/src/main/java/com/example/zkp_android_spartan/uniffi/noir_spartan/noir_spartan.kt`; regenerate it after API changes.
- The Android app uses JNA (via `net.java.dev.jna:jna:5.19.1`) to load the native library.
- `app/build.gradle.kts` configures `androidResources { noCompress += "gz" }` so that `.gz` witness files are stored as-is in the APK. Without this, AAPT compresses them and `AssetManager.open()` returns decompressed bytes, breaking the prover.
- The Android app is a tabbed Compose screen. It discovers the circuit directories under `assets/spartan-backend/circuits/`, copies the selected circuit to `filesDir/circuits/<name>/`, and calls `generate_proof` with that filesystem path.
- The vendored `spartan-backend` is a large Rust codebase that pulls in a forked Noir compiler/toolchain; initial builds can be slow.
- Circuit target artifacts (`circuits/*/target/`) are intentionally versioned in this repository because the generated ACIR bytecode is part of the tracked backend test corpus.
- For circuit compilation from source, use the `nargo-t256` build of the `eid-privacy/noir` fork at tag `t256-v0.22-1`.
- Keep `generate_proof(pwd: String) -> bool` unchanged; richer error reporting was intentionally left out to preserve the existing FFI signature. The Android layer logs full exceptions and relies on the Rust-side `eprintln!` for failure details.

## License

Mozilla Public License 2.0 (see `LICENSE`).
