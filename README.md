# zkp-android-spartan

An experimental Android project exploring zero-knowledge proof (ZKP) workflows using [Noir](https://noir-lang.org/) together with a [Spartan2](https://github.com/microsoft/Spartan)-style proof system. The goal is to provide a working template for running ZKP operations on Android, with the heavy lifting implemented in Rust and exposed to the Android app through [UniFFI](https://mozilla.github.io/uniffi-rs/).

> **Status:** The `noir_spartan` Rust crate exposes a real `generate_proof` function backed by a full Spartan backend, and the repository includes a suite of passing Noir circuits. The Android app now provides a tabbed Jetpack Compose UI with a **Generate proof** button per circuit and reports success/failure with elapsed time. UniFFI Kotlin bindings and the native `.so` libraries still need to be generated from `noir_spartan` before the app can run.

## What this project contains

- **`app/`** — Android application module.
  - Built with Gradle, the Android Gradle Plugin 9.0.1, Kotlin 2.0.21, and Jetpack Compose.
  - Targets `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`.
  - Entry point is `MainActivity.kt`, which renders a tabbed Compose UI: one tab per circuit in `assets/spartan-backend/circuits/`, each with a **Generate proof** button and a status read-out.

- **`noir_spartan/`** — Rust crate that exposes ZKP utilities to Android.
  - Uses UniFFI 0.31 (proc-macro exports) to generate Kotlin bindings.
  - Exports `generate_proof(pwd: String) -> bool`, which loads a Noir circuit directory and produces a Spartan2 proof via `spartan-backend`.
  - Depends on `spartan-backend` from the [`eid-privacy/spartan-backend`](https://github.com/eid-privacy/spartan-backend) repository.

- **`app/src/main/assets/spartan-backend/`** — Vendored/included Spartan backend and Noir circuits.
  - `spartan-backend/` — Rust crate that reads Noir `ProgramArtifact`s, synthesizes R1CS constraints, and proves/verifies with Spartan2.
  - `circuits/` — Incrementally complex Noir circuits used to exercise the backend (e.g. `c0000_trivial`, `c0006_sha256`, `c0102_signature_vanilla_equation`, `c0200_swiyu_jwt`).
  - `preprocessing/` — Standalone Cargo tools that precompute ECDSA-related witnesses off-circuit and inject them into circuit `Prover.toml` files.
  - `algebra-utils/` — Shared elliptic-curve helpers used by the backend and preprocessing tools.

## Build instructions

### Android app

Requires a working Android SDK and JDK 17+.

```bash
./gradlew build
./gradlew test
```

### Rust crate

Requires a Rust toolchain. The first build downloads and compiles the `spartan-backend` dependency, which can take a while.

```bash
cd noir_spartan
cargo build
cargo test
```

### Spartan backend and circuits (inside assets)

The backend can be built and run standalone to prove/verify any circuit in `app/src/main/assets/spartan-backend/circuits/`:

```bash
cd app/src/main/assets/spartan-backend/spartan-backend
cargo run --release c0000_trivial
```

Run without a circuit name to execute all known passing circuits. See `app/src/main/assets/spartan-backend/README.md` for full details on compiling circuits with the `nargo-t256` fork and running benchmarks.

### Generating UniFFI bindings and Android binaries

After making changes to `noir_spartan`, rebuild the native library and regenerate Kotlin bindings:

```bash
cd noir_spartan
cargo ndk -t arm64-v8a -t x86_64 -o ./jniLibs build --release
cp -R ./jniLibs/ ../app/src/main/
cargo run --bin uniffi-bindgen generate --library ./target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/
```

Adjust target architectures, library path, and output directory as needed.

## Project roadmap / known limitations

1. The Android UI is implemented, but the UniFFI bindings and native libraries must be built in an environment with Rust + the Android NDK before the app can call `generate_proof`.
2. The `noir_spartan` crate currently only exports a single `generate_proof` function; richer APIs (returning proof bytes, verification, circuit selection) are not yet exposed.
3. Noir compilation and Spartan proving are resource-intensive; performance tuning for mobile will be needed.
4. Some circuits require off-circuit ECDSA preprocessing; run the tools in `app/src/main/assets/spartan-backend/preprocessing/` before proving them. Circuits that are missing preprocessed witnesses will report a failure in the app.

## License

This project is licensed under the Mozilla Public License 2.0. See the [LICENSE](LICENSE) file for details.
