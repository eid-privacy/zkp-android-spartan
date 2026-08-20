# zkp-android-spartan

An experimental Android project exploring zero-knowledge proof (ZKP) workflows using [Noir](https://noir-lang.org/) together with a [Spartan2](https://github.com/microsoft/Spartan)-style proof system. The goal is to provide a working template for running ZKP operations on Android, with the heavy lifting implemented in Rust and exposed to the Android app through [UniFFI](https://mozilla.github.io/uniffi-rs/).

> **Status:** The `noir_spartan` Rust crate exposes real `generate_proof` and `verify` functions backed by a full Spartan backend, and the repository includes a suite of passing Noir circuits. The Android app provides a tabbed Jetpack Compose UI with **Generate proof** and **Verify proof** buttons per circuit and reports success/failure with elapsed time. The UniFFI Kotlin bindings and native `.so` libraries are **generated artifacts**; they happen to be present in this workspace, but they are listed in `.gitignore` and are not committed. A fresh clone must rebuild them before the app can run.

## What this project contains

- **`app/`** — Android application module.
  - Built with Gradle 9.5.0, the Android Gradle Plugin 9.3.1, Kotlin 2.2.10, and Jetpack Compose.
  - Targets `compileSdk = 37`, `minSdk = 24`, `targetSdk = 36`.
  - Entry point is `MainActivity.kt`, which renders a tabbed Compose UI: one tab per directory in `assets/spartan-backend/circuits/`, each with **Generate proof** and **Verify proof** buttons and a status read-out. A proof must be generated before the **Verify proof** button is enabled.
  - `AssetUtils.kt` copies circuit directories from assets to `filesDir/circuits/` and includes a fallback that re-gzips witness files if the Android build tools strip or decompress the `.gz` extension.

- **`noir_spartan/`** — Rust crate that exposes ZKP utilities to Android.
  - Uses UniFFI 0.31 (proc-macro exports) to generate Kotlin bindings.
  - Uses Rust `edition = "2024"`.
  - Exports `generate_proof(pwd: String) -> String`, which loads a Noir circuit directory, produces a Spartan2 proof via `spartan-backend`, and returns the base64-encoded proof (or an empty `String` on failure).
  - Exports `verify(pwd: String, proof: String) -> bool`, which loads the same Noir circuit directory and verifies a base64-encoded proof, returning `true` on success and `false` on failure.
  - Depends on `spartan-backend` from the [`eid-privacy/spartan-backend`](https://github.com/eid-privacy/spartan-backend) repository via an **SSH Git URL** (`ssh://git@github.com/eid-privacy/spartan-backend`).

- **`app/src/main/assets/spartan-backend/`** — Git submodule containing the vendored Spartan backend and Noir circuits.
  - `spartan-backend/` — Rust crate that reads Noir `ProgramArtifact`s, synthesizes R1CS constraints, and proves/verifies with Spartan2.
  - `circuits/` — Incrementally complex Noir circuits used to exercise the backend (e.g. `c0000_trivial`, `c0006_sha256`, `c0102_signature_vanilla_equation`, `c0200_swiyu_jwt`).
  - `circuits/c9000_benchmark/` — Benchmark circuit without pre-built `target/` artifacts.
  - `circuits/eid/` — Shared Noir library used by `c0200_swiyu_jwt`, not a standalone circuit.
  - `preprocessing/` — Standalone Cargo tools that precompute ECDSA-related witnesses off-circuit and inject them into circuit `Prover.toml` files.
  - `algebra-utils/` — Shared elliptic-curve helpers used by the backend and preprocessing tools.

## Build instructions

### Prerequisites

- Android SDK and JDK 17+.
- Rust toolchain supporting `edition = "2024"`.
- [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk) for cross-compiling Android native libraries.
- SSH access to `git@github.com:eid-privacy/spartan-backend` so that `noir_spartan/Cargo.toml` can fetch its git dependency.

### Android app

```bash
./gradlew build
./gradlew test
```

The Gradle build expects the native libraries to already exist under `app/src/main/jniLibs/`. It does not invoke `cargo ndk` automatically.

### Rust crate

```bash
cd noir_spartan
cargo build --release
cargo test
```

> **Note:** The UniFFI binding generator reads the exported API from the compiled library, so you must build the release `.so` (`target/release/libnoir_spartan.so`) before regenerating Kotlin bindings.

### Android native libraries and UniFFI bindings

After changing the Rust API, rebuild the host library, regenerate the Kotlin bindings from it, then build the Android `.so` files and copy them into the app module:

```bash
cd noir_spartan

# 1. Rebuild the host release library that uniffi-bindgen inspects
cargo build --release

# 2. Regenerate Kotlin bindings from the freshly built .so
cargo run --bin uniffi-bindgen generate --library ./target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/

# 3. Build Android native libraries and copy them into the app module
cargo ndk -t arm64-v8a -t x86_64 -o ./jniLibs build --release
cp -R ./jniLibs/ ../app/src/main/
```

Adjust target architectures, library path, and output directory as needed. The order matters: UniFFI emits bindings from the `.so` on disk, and the Android app loads the `.so` from `jniLibs`, so both must be rebuilt after any Rust API change.

### Spartan backend and circuits (inside assets submodule)

To compile the circuits for the Android app,

1. `cd app/src/main/assets/spartan-backend/spartan-backend`
2. `devbox run build`
3. `devbox run start`

The backend can be built and run standalone to prove/verify any circuit in `app/src/main/assets/spartan-backend/circuits/`:

```bash
cd app/src/main/assets/spartan-backend/spartan-backend
cargo run --release c0000_trivial
```

Running without a circuit name executes only the default hardcoded subset (`c0000`–`c0100`). To run circuits such as `c0006_sha256`, `c0101_signature_pok_zkattest_style`, `c0102_signature_vanilla_equation`, or `c0200_swiyu_jwt`, pass the circuit name explicitly.

See `app/src/main/assets/spartan-backend/README.md` for full details on compiling circuits with the `nargo-t256` fork and running benchmarks.

## Project roadmap / known limitations

1. The UniFFI Kotlin bindings and native `.so` libraries are generated artifacts. They are present in this workspace but are not committed; a fresh clone must run the `cargo ndk` and `uniffi-bindgen` steps above.
2. The `noir_spartan` crate exports `generate_proof` and `verify`; richer APIs (circuit selection, batch operations) are not yet exposed.
3. `noir_spartan` depends on `spartan-backend` via an SSH Git URL, while the circuit assets live in a Git submodule. The submodule is currently at commit `71f8d0d9...`, but `noir_spartan/Cargo.lock` resolves `spartan-backend` to commit `2b4f34db...`; these may not match.
4. Noir compilation and Spartan proving are resource-intensive; performance tuning for mobile will be needed.
5. Some circuits require off-circuit ECDSA preprocessing; run the tools in `app/src/main/assets/spartan-backend/preprocessing/` before proving them. Circuits that are missing preprocessed witnesses will report a failure in the app.
6. The Android UI discovers every directory under `assets/spartan-backend/circuits/`, including `c9000_benchmark/` (no `target/` artifacts) and `eid/` (a shared library, not a circuit). Pressing **Generate proof** on these tabs will fail.

## License

This project is licensed under the Mozilla Public License 2.0. See the [LICENSE](LICENSE) file for details.
