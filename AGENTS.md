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

- The `noir_spartan` crate is currently a stub: it exposes no useful functions in `noir_spartan.udl` and `lib.rs` only contains a placeholder `add` function. Real ZKP/Noir integration is not yet implemented.
- The Noir dependency points at a large external Git repository; expect long initial build times and ensure the Rust toolchain is installed.
- Android app code is generated from Android Studio's Compose template and does not yet call into the Rust crate.

## License

Mozilla Public License 2.0 (see `LICENSE`).
