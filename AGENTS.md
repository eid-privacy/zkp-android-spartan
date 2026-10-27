# zkp-android-spartan — Agent Guide

## Project Purpose

`zkp-android-spartan` is an experimental Android application that benchmarks zero-knowledge proof (ZKP) workflows built with a [Spartan2](https://github.com/microsoft/Spartan)-style proof system through the [Noir](https://noir-lang.org/) language/toolchain. The project consists of:

- A standard Android app skeleton (`app/`).
- A Rust crate (`noir_spartan/`) that exposes ZKP utilities to Android via [UniFFI](https://mozilla.github.io/uniffi-rs/).
- A vendored/included Spartan backend and Noir circuits under `app/src/main/assets/spartan-backend/` (managed as a Git submodule).

The Rust side is functional (it can precompute, prove, and verify real Noir circuits), and the Android UI is a menu-driven Compose screen with a **Benchmarks** page plus one page per benchmark circuit (`c0200_swiyu_jwt`, `c0202_sicpa_backend_constant`), each with **Precompute proof**, **Generate proof**, **Generate proof (precomputed)**, and **Verify proof** buttons. The UniFFI Kotlin bindings and compiled `.so` libraries are **generated artifacts**.

## Repository Layout

```
zkp-android-spartan/
├── AGENTS.md              # This file
├── README.md              # Human-oriented project overview
├── LICENSE                # Mozilla Public License 2.0
├── .gitignore             # Excludes generated Rust targets, jniLibs, UniFFI bindings, local.properties
├── .gitmodules            # Defines the spartan-backend Git submodule
├── build.gradle.kts       # Root Gradle build file
├── settings.gradle.kts    # Gradle project settings
├── gradle.properties      # Gradle/Android configuration
├── gradle/
│   ├── libs.versions.toml # Version catalog (AGP 9.3.3, Kotlin 2.2.10, Compose BOM 2024.09.00)
│   ├── gradle-daemon-jvm.properties # Daemon JVM toolchain configuration (version 21)
│   └── wrapper/           # Gradle 9.5.0 wrapper
├── app/                   # Android application module
│   ├── build.gradle.kts   # App-level dependencies and build config
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/spartan-backend/   # Git submodule: vendored Spartan backend + Noir circuits
│       │   │   ├── spartan-backend/      # Rust crate: R1CS synthesis, proving, verification
│       │   │   ├── circuits/             # Noir circuits (c0000_trivial, c0006_sha256, ...)
│       │   │   ├── preprocessing/        # Off-circuit ECDSA witness precomputations
│       │   │   ├── algebra-utils/        # Shared elliptic-curve helpers
│       │   │   └── scripts/              # Helper scripts
│       │   ├── java/com/example/zkp_android_spartan/
│       │   │   ├── MainActivity.kt          # Jetpack Compose entry point (menu-driven UI: Benchmarks page + circuit pages)
│       │   │   ├── AssetUtils.kt            # Copies circuit directories from assets to filesDir
│       │   │   ├── uniffi/noir_spartan/noir_spartan.kt # Generated UniFFI Kotlin bindings (gitignored)
│       │   │   └── ui/theme/                # Compose Material3 theme files
│       │   ├── jniLibs/                     # Pre-built native libraries (gitignored)
│       │   │   ├── arm64-v8a/libnoir_spartan.so
│       │   │   └── x86_64/libnoir_spartan.so   # only built on x86_64 hosts
│       │   └── res/                         # Android resources
└── noir_spartan/          # Rust crate exposed to Android
    ├── Cargo.toml         # Rust package manifest (UniFFI 0.31, spartan-backend git dep, tag v1.0.0)
    ├── Cargo.lock         # Resolved dependency graph
    ├── uniffi.toml        # UniFFI binding configuration (Kotlin package / cdylib name)
    ├── src/
    │   ├── lib.rs         # Library entry point (UniFFI scaffolding), re-exports generate_proof
    │   └── spartan.rs     # UniFFI-exported precompute_proof, generate_proof, generate_proof_precomputed, verify implementations
    └── src/bin/
        └── uniffi-bindgen.rs  # CLI binary for regenerating Kotlin bindings
```

## Technology Stack

- **Android**: Gradle 9.5.0 with Android Gradle Plugin 9.3.3, `compileSdk = 37`, `minSdk = 24`, `targetSdk = 36`.
- **Language**: Kotlin 2.2.10, Java 11 source/target compatibility.
- **UI**: Jetpack Compose with Material3, Compose BOM `2024.09.00`, Compose Foundation Pager, and Kotlin Coroutines.
- **Native/Rust**: Rust crate (`noir_spartan`) using `edition = "2024"` and UniFFI 0.31 proc-macro exports to generate bindings for Kotlin/Android.
- **ZKP/Noir**: `noir_spartan` depends on `spartan-backend` from the `eid-privacy/spartan-backend` repository via the public HTTPS Git URL `https://github.com/eid-privacy/spartan-backend`, tag `v1.0.0` (the committed `Cargo.lock` resolves it to commit `ffa297c1b3eaed20cbe5cb2160e574b97ca4196f`). The vendored copy under `assets/spartan-backend/` is used for circuit assets and standalone backend runs, but it is **not** the dependency source for the `noir_spartan` crate. Developers sometimes switch the dependency to a local path (e.g. `path = "../../spartan-backend/spartan-backend/"`, a sibling clone); treat that as a developer-local override and keep it out of commits.
  The crate exports four UniFFI functions:
  - `precompute_proof(pwd: String) -> String` — loads the Noir circuit directory, runs the offline prep phase, writes the reusable artifacts to `<circuit_dir>/target/precompute.bin`, and returns that file's absolute path (or an empty `String` on failure).
  - `generate_proof(pwd: String) -> String` — loads a Noir circuit directory, produces a Spartan2 proof via `spartan-backend`, encodes it as base64, and returns it (or an empty `String` on failure).
  - `generate_proof_precomputed(pwd: String, path: String) -> PrecomputedProof` — same, but reuses the precompute artifact at the explicitly passed `path` (as returned by `precompute_proof`); returns a `PrecomputedProof` record with the base64 `proof` (empty on failure), `load_ms` (time to read and decode `precompute.bin`) and `prove_ms` (online proving time). It re-implements `spartan_backend::prove_with_precompute` (via the public `precompute::load` and `OnlineProver::prove_online`) to measure both phases separately.
  - `verify(pwd: String, proof: String) -> bool` — loads the circuit and verifies a base64-encoded proof, returning `true` on success and `false` on failure.
- **Logging**: `noir_spartan` routes Rust logs to Android Logcat. On Android it initializes `android_logger` (tag `noir_spartan`, Info level) and installs a `tracing_subscriber` whose `MakeWriter` bridges to the `log` crate, so `tracing` output from `spartan-backend` shows up in Android Studio's Logcat. On non-Android targets the same writer prints to stdout.
- The backend uses a forked Noir compiler (`t256-v0.26-1` tag, noir beta_26) and Spartan2.

## Build Commands

### Android

- `./gradlew build` — Build the Android app.

> **Note:** The Gradle build does **not** compile the Rust code. It expects the native libraries to already exist under `app/src/main/jniLibs/`. See the `cargo ndk` step below.

### `noir_spartan` Rust crate

Requires a Rust toolchain that supports `edition = "2024"`. The first build downloads and compiles the `spartan-backend` dependency, which can take a while.

- `cargo build --release` — Build the Rust crate. The release `.so` is also the source of truth for UniFFI binding generation.
- `cargo test` — Run Rust unit tests.
- `cargo run --bin uniffi-bindgen generate --library ./target/release/libnoir_spartan.so --language kotlin --out-dir ../app/src/main/java/com/example/zkp_android_spartan/` — Regenerate Kotlin bindings after API changes. This writes the file to `app/src/main/java/com/example/zkp_android_spartan/uniffi/noir_spartan/noir_spartan.kt`. **This command inspects the compiled `target/release/libnoir_spartan.so`, so run `cargo build --release` first.**

### Android native libraries

After any Rust API change, rebuild the `.so` files for the target Android architectures and copy them into the app module:

```bash
cd noir_spartan
cargo ndk -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release
```

The command writes the `.so` files directly into `app/src/main/jniLibs/`. `devbox run ndk_build` always builds `arm64-v8a` and adds `x86_64` only on x86_64 hosts: `vega-prover` enables `halo2curves`' `asm` feature for x86_64 targets, and the `halo2curves` build script checks the *host* architecture, so the x86_64 build fails on arm64 hosts (Apple Silicon) with "Currently feature `asm` can only be enabled on x86_64 arch." Emulators on arm64 hosts are arm64, so that slice is not needed there. Because UniFFI embeds a per-function checksum in both the generated Kotlin and the loaded native library, the Kotlin bindings and `jniLibs/.so` must always come from the same build.

### Spartan backend and circuits

From `app/src/main/assets/spartan-backend/spartan-backend/`:

- `cargo run --release c0000_trivial` — Prove and verify a specific circuit.
- `cargo run --release` — Prove and verify the default hardcoded subset of circuits (`c0000`–`c0100`).

Set `RUST_LOG=DEBUG` (or `INFO`, `TRACE`) for detailed logs.

### Preprocessing tools

Some circuits need off-circuit ECDSA witnesses. Each tool is a standalone Cargo crate:

```bash
cd app/src/main/assets/spartan-backend/preprocessing/<tool>
cargo run --release
```

See `app/src/main/assets/spartan-backend/preprocessing/README.md` for the tool-to-circuit mapping.

## Devbox and Emulator Workflow

The toolchain (Rust, JDK 17, Android platform tools, gcc/cmake) is pinned per-project with **devbox + nix** rather than global installs, via `devbox.json` at the repo root. Every command below runs inside the devbox environment (prefix with `devbox run`, or open a shell with `devbox shell`). The `init_hook` detects the OS and sets `ANDROID_HOME` (`$HOME/Android` on Linux, `$HOME/Library/Android/sdk` on macOS), sets `ANDROID_SDK_ROOT`, `JAVA_HOME`, and `ANDROID_NDK_HOME` (auto-detected from `$ANDROID_HOME/ndk/*`), puts `~/.cargo/bin` on `PATH`, sets `PKG_CONFIG_PATH`, and runs `rustup default stable`. All git dependencies are public HTTPS URLs, so no SSH key is needed.

Prerequisites once per clone: `git submodule update --init` (pulls the `spartan-backend` assets), `devbox run compile_circuits` (generates the circuit `target/` artifacts, which the submodule no longer versions), and `devbox install` (`cargo-ndk` is a devbox package, and the devbox `init_hook` installs the `aarch64-linux-android` and `x86_64-linux-android` Rust targets). Ensure `local.properties` has `sdk.dir=` pointing at a complete Android SDK including an NDK (`~/Android` on Linux, `~/Library/Android/sdk` on macOS, matching `devbox.json`).

Devbox scripts (`devbox run <name>`):

```bash
devbox run compile_circuits   # nargo-t256 compile + execute the benchmark circuits in the submodule (scripts/compile_circuits.sh, uses the submodule's devbox)
devbox run rust_build         # cargo build --release in noir_spartan (host libnoir_spartan.so)
devbox run gen_bindings       # regenerate the UniFFI Kotlin bindings (uses --no-format; ktlint is not installed; picks libnoir_spartan.dylib on macOS, .so elsewhere)
devbox run ndk_build          # cargo ndk build .so for arm64-v8a (+ x86_64 on x86_64 hosts) directly into app/src/main/jniLibs
devbox run build_apk          # ./gradlew :app:assembleDebug
devbox run ensure_avd         # create the Pixel_9 AVD if missing (scripts/ensure_avd.sh)
devbox run emulator           # ensure_avd, then start Pixel_9 windowed with 8 GB RAM (-memory, override with AVD_RAM_MB)
devbox run emulator-headless  # ensure_avd, then start Pixel_9 with 8 GB RAM, -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
devbox run install_apk        # adb install -r the debug APK
devbox run launch_app         # adb am start the MainActivity (installs the APK first if missing)
devbox run logcat             # adb logcat filtered to the app's PID (full logcat if the app is not running); extra args go to adb logcat
devbox run cargo <args>       # pass-through to cargo
devbox run gradle <args>      # pass-through to ./gradlew
```

The generated bindings (`app/src/main/java/.../uniffi/`) and native libs (`app/src/main/jniLibs/`) are **git-ignored**, so a fresh clone must run `gen_bindings` and `ndk_build` before the app will compile and run. `ensure_avd.sh` picks the emulator ABI matching the host: on x86_64 Linux the emulator runs the x86_64 slice, on Apple Silicon it runs the arm64-v8a slice, which is also what physical devices use.

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

## Important Notes

- The `noir_spartan` crate is no longer a stub: `src/spartan.rs` exports `precompute_proof(pwd: String) -> String`, `generate_proof(pwd: String) -> String`, `generate_proof_precomputed(pwd: String, path: String) -> PrecomputedProof`, and `verify(pwd: String, proof: String) -> bool`. `precompute_proof` runs the offline prep phase and returns the absolute path of the written `<circuit_dir>/target/precompute.bin` artifact (empty `String` on failure). `generate_proof` produces a monolithic Spartan2 proof and returns it base64-encoded (empty `String` on failure). `generate_proof_precomputed` produces a proof reusing the precompute artifact at the explicitly passed path and returns it together with the `precompute.bin` load time and the online proving time (empty `proof` on failure). `verify` verifies a base64-encoded proof, returning `true` on success and `false` on failure. Rust logs are surfaced to Android Logcat (tag `noir_spartan`) via `android_logger` plus a `tracing` → `log` bridge.
- UniFFI bindings are generated from proc-macro attributes (`#[uniffi::export]`) by inspecting the compiled `.so`. The Kotlin binding file is written to `app/src/main/java/com/example/zkp_android_spartan/uniffi/noir_spartan/noir_spartan.kt`, but the `uniffi/` directory is listed in `.gitignore` and is not committed. Regenerate it after API changes, and always rebuild `target/release/libnoir_spartan.so` first.
- The Android app uses JNA (via `net.java.dev.jna:jna:5.19.1`) to load the native library.
- `app/build.gradle.kts` configures `androidResources { noCompress += "gz" }` so that `.gz` witness files are stored as-is in the APK. Without this, AAPT compresses them and `AssetManager.open()` returns decompressed bytes, breaking the prover.
- `AssetUtils.kt` includes additional fallback logic: if the Android build tools strip the `.gz` extension from a compressed witness asset (stored as `target/<circuit>`), the helper re-gzips it into `target/<circuit>.gz`.
- `noir_spartan` loads circuits with `instantiate_prover_circuit_from_dir` for the three prover calls and `instantiate_verifier_circuit_from_dir` for `verify`, so verification never reads the private witness and proving never reads `verifier_input.json`.
- `copyCircuitFromAssets` overwrites the asset files but never deletes the circuit directory, so the Rust-produced `target/precompute.bin` (not shipped in assets) survives re-copies and the path returned by `precompute_proof` stays valid.
- The Android app is a menu-driven Compose screen with vertically scrollable content. A collapsible dropdown **Menu** switches between a **Benchmarks** page and one page per benchmark circuit. Only the circuits listed in `BENCHMARK_CIRCUITS` (`c0200_swiyu_jwt`, `c0202_sicpa_backend_constant`) that actually exist under `assets/spartan-backend/circuits/` are shown — the app no longer surfaces every circuit directory. Each circuit page has **Precompute proof**, **Generate proof (precomputed)**, **Generate proof**, and **Verify proof** buttons; **Generate proof (precomputed)** is enabled only after a successful precomputation, and **Verify proof** only after a proof has been generated.
- The **Benchmarks** page has two buttons per circuit: *generation + verification* (5 × `generateProof`, then 5 × `verify`, see `BENCHMARK_ITERATIONS` in `MainActivity.kt`) and *precomputed generation + verification* (1 × timed `precomputeProof`, 5 × `generateProofPrecomputed`, 5 × `verify`), reporting Runs/Shortest/Longest/Average per operation. The precomputed benchmark additionally reports *precompute.bin load* and *Online proving* rows, taken from the `PrecomputedProof` returned by Rust; the circuit page shows both times after *Generate proof (precomputed)*. Every call's result is checked; a failure aborts the benchmark with an error status instead of reporting timings of failed runs. `prove_circuit` never reads `precompute.bin`, so the monolithic benchmark is unaffected by a leftover artifact. Timings are per UniFFI call and include circuit loading from disk (and, for the precomputed path, reading `precompute.bin`). The benchmark coroutine is scoped to the Benchmarks pager page, so switching pages cancels it. `MainActivity` declares `android:configChanges` for orientation/screen-size changes, so rotating the device does not recreate the activity: results, status texts and running benchmarks survive rotation (UI state is plain Compose `remember`, not a ViewModel).
- Not every directory under `assets/spartan-backend/circuits/` is a standalone provable circuit (e.g. `c9000_benchmark/` has no `target/` artifacts or `Prover.toml`, and `eid/` is a shared Noir library used by `c0200_swiyu_jwt`). This is why the app only surfaces the curated `BENCHMARK_CIRCUITS` list instead of auto-discovering every directory.
- The prover needs well over 2 GB of RAM. With the emulator's default 2.5 GB, Android's low-memory killer kills the app during proof generation (`lowmemorykiller: Kill 'com.example.zkp_android_spartan' ...`, no Java or native crash). The devbox emulator scripts therefore pass `-memory ${AVD_RAM_MB:-8192}`. Setting `hw.ramSize` in the AVD's `config.ini` does not work: emulator 36.6.11 ignores it and logs `Increasing RAM size to 2560MB`.
- `noir_spartan` uses `mimalloc` as its global allocator (`src/lib.rs`). On the arm64 emulator this made proof generation ~23% and verification ~30% faster than Android's default Scudo allocator. A `[profile.release]` with `lto = "fat"` and `codegen-units = 1` was also measured and gave no improvement, so it is not used.
- The vendored `spartan-backend` is a large Rust codebase that pulls in a forked Noir compiler/toolchain; initial builds can be slow.
- Circuit target artifacts (`circuits/*/target/`) are **not** versioned in the submodule anymore (since submodule commit `040e23c`), even though the submodule README still says otherwise. Without them the app finds no circuits (`CircuitDebug: Number of circuits found: 0`) and the Benchmarks page shows only its title. Run `devbox run compile_circuits` after checking out or updating the submodule, then rebuild and reinstall the APK.
- `compile_circuits` uses the `nargo-t256` pinned by the submodule's own `devbox.json`. A `devbox run -- bash -c '...'` one-liner loses `$variables` because devbox re-quotes its arguments, which is why the logic lives in a script file.
- The `noir_spartan` crate depends on `spartan-backend` via its Git URL, not via the local submodule path. (A developer-local path override may be used — see the Technology Stack section.)
- The `spartan-backend` submodule is pinned at tag `v1.0.0` (commit `ffa297c1b3eaed20cbe5cb2160e574b97ca4196f`, on `main`), and the committed `noir_spartan/Cargo.lock` resolves the `spartan-backend` git dependency to the same commit. Pin to tags or commits on `main`: feature branches get deleted after merging, which breaks fresh clones. The two are updated independently, so if they drift apart the vendored code under `assets/spartan-backend/` is not guaranteed to match the code linked into the Android library.
- If you change any `#[uniffi::export]` API (for example, `precompute_proof` was recently changed from `-> bool` to `-> String` and `generate_proof_precomputed` gained an explicit path argument), remember to (1) rebuild `target/release/libnoir_spartan.so`, (2) regenerate the Kotlin bindings, and (3) rebuild the Android `jniLibs/.so` files. The Android Kotlin layer must then be updated to match the new signature (`String` stays `String`; `Vec<u8>` becomes `kotlin.ByteArray`).

## License

Mozilla Public License 2.0 (see `LICENSE`).
