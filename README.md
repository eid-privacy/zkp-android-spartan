# zkp-android-spartan

An experimental Android project exploring zero-knowledge proof (ZKP) workflows using [Noir](https://noir-lang.org/) together with a [Spartan](https://github.com/microsoft/Spartan)-style proof system. The goal is to provide a working template for running ZKP operations on Android, with the heavy lifting implemented in Rust and exposed to the Android app through [UniFFI](https://mozilla.github.io/uniffi-rs/).

> **Status: early skeleton.** The Android app is currently a standard Jetpack Compose starter, and the `noir_spartan` Rust crate is a stub that does not yet expose any real proving functionality.

## What this project contains

- **`app/`** — Android application module.
  - Built with Gradle, the Android Gradle Plugin 9.0.1, Kotlin 2.0.21, and Jetpack Compose.
  - Targets `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
  - Entry point is `MainActivity.kt`, which renders a simple Compose UI.

- **`noir_spartan/`** — Rust crate intended for ZKP/Noir logic.
  - Uses UniFFI 0.31 to generate Kotlin bindings for Android.
  - Depends on the Noir compiler/toolchain from the `v1.0.0-beta.22` branch.
  - Currently contains only a placeholder `add` function and an empty interface definition.

## Build instructions

### Android app

Requires a working Android SDK and JDK 17+.

```bash
./gradlew build
./gradlew test
```

### Rust crate

Requires a Rust toolchain (and patience — the first build compiles the Noir dependency).

```bash
cd noir_spartan
cargo build
cargo test
```

### Generating UniFFI bindings

Once the Rust crate exposes real functions, you can regenerate Kotlin bindings with:

```bash
cd noir_spartan
cargo ndk -t arm64-v8a -t x86_64 -o ./jniLibs build --release
cp -R ./jniLibs/ ../app/src/main/
cargo build --release
cargo run --bin uniffi-bindgen generate --library target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/
```

Adjust the library path and output directory as needed.

## Project roadmap / known limitations

1. `noir_spartan/src/lib.rs` contains only a stub `add` function; real ZKP proving/verification code is not implemented.
2. The Android app does not currently load or call the Rust library.
3. Noir compilation and Spartan proving can be resource-intensive; performance tuning for mobile will be needed.

## License

This project is licensed under the Mozilla Public License 2.0. See the [LICENSE](LICENSE) file for details.
