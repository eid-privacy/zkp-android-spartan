# zkp-android-spartan

An experimental Android app that generates and verifies zero-knowledge proofs (ZKP) on the phone. Circuits are written in [Noir](https://noir-lang.org/) and proved with a [Spartan2](https://github.com/microsoft/Spartan)-style backend ([`eid-privacy/spartan-backend`](https://github.com/eid-privacy/spartan-backend)). The proving code is Rust, and the app calls it from Kotlin through [UniFFI](https://mozilla.github.io/uniffi-rs/).

The main purpose of the app is to **benchmark proof generation and verification on Android** for the eID circuits `c0200_swiyu_jwt` and `c0202_sicpa_backend_constant`. Results are in [Benchmarks](#benchmarks).

> **Demo code.** This is research code from the [eid-privacy](https://github.com/eid-privacy) project. It has not been audited and must not be used in production.

## How it fits together

```
MainActivity.kt (Compose UI, benchmarks)
      │  calls precomputeProof / generateProof / generateProofPrecomputed / verify
      ▼
uniffi/noir_spartan/noir_spartan.kt   ← generated Kotlin bindings (not committed)
      │  JNA
      ▼
jniLibs/<abi>/libnoir_spartan.so      ← built from noir_spartan/ (not committed)
      │
      ▼
spartan-backend (Rust, fetched by cargo, tag v1.0.0)
      │  reads circuit files from
      ▼
filesDir/circuits/<circuit>/          ← copied from assets/spartan-backend/circuits/
```

| Path | Contents |
|------|----------|
| `app/` | Android app (Kotlin, Jetpack Compose). |
| `noir_spartan/` | Rust crate that wraps `spartan-backend` and exports it to Kotlin. |
| `app/src/main/assets/spartan-backend/` | Git submodule of `spartan-backend`. The app only uses its `circuits/` directory as assets; the Rust code linked into the app comes from the git dependency in `noir_spartan/Cargo.toml`. |
| `devbox.json` | Pinned toolchain (Rust, JDK 17, Android tools) plus helper scripts. |
| `benchmarks/<device>/zkp.log` | Raw benchmark logs (logcat, filtered). |
| `scripts/ensure_avd.sh` | Creates the `Pixel_9` emulator if it is missing. |

## The Rust API (`noir_spartan`)

`pwd` is always the circuit directory on the device, `filesDir/circuits/<circuit>`. It must contain `target/<circuit>.json` (ACIR), `target/<circuit>.gz` (witness) and `verifier_input.json`.

| Rust | Kotlin | What it does |
|------|--------|--------------|
| `precompute_proof(pwd) -> String` | `precomputeProof` | Runs the offline phase once, writes `<pwd>/target/precompute.bin`, and returns its path. Returns `""` on failure. |
| `generate_proof(pwd) -> String` | `generateProof` | Generates a proof in one step (monolithic) and returns it as base64. Returns `""` on failure. |
| `generate_proof_precomputed(pwd, path) -> PrecomputedProof` | `generateProofPrecomputed` | Loads `precompute.bin` from `path`, runs only the online phase, and returns a `PrecomputedProof` with the base64 `proof` (`""` on failure), the `precompute.bin` load time `load_ms` and the online proving time `prove_ms`. |
| `verify(pwd, proof) -> Boolean` | `verify` | Verifies a base64 proof against the circuit's public inputs. |

Every call loads the circuit from disk again: the prover calls read the ACIR and the witness, `verify` reads the ACIR and `verifier_input.json`. Rust panics reach Kotlin as exceptions. Rust logs appear in Logcat under the tag `noir_spartan`.

## Using the app

Use the **Menu** at the top left to switch pages:

- **One page per circuit** with four buttons:
  - **Precompute proof**: runs the offline phase.
  - **Generate proof (precomputed)**: available after precomputing.
  - **Generate proof**: generates a proof in one step.
  - **Verify proof**: available after a proof has been generated.

  Each button shows whether it succeeded and how long it took.
- **Benchmarks**: each circuit has two buttons.
  - *Benchmark (generation + verification)*: 5 × `generateProof`, then 5 × `verify` on the last proof.
  - *Benchmark (precomputed generation + verification)*: 1 × `precomputeProof`, then 5 × `generateProofPrecomputed`, then 5 × `verify`.

  The results show the number of runs and the shortest, longest and average time for each step. A benchmark stops with an error as soon as a call fails, so failed runs never show up as timings.

**What the timings include.** Each time is the wall-clock time of a single Kotlin → Rust call, measured with `SystemClock.elapsedRealtime()` (millisecond resolution). That includes loading the circuit files, and for the precomputed variant also reading and checking `precompute.bin`. Copying assets into `filesDir` is not included. Keep the Benchmarks page open while a benchmark runs: switching pages cancels the run.

## Building

### One-time setup

```bash
git submodule update --init             # circuit sources
devbox run compile_circuits             # compile them with nargo-t256; target/ is not versioned in the submodule
devbox install                          # toolchain from devbox.json, incl. cargo-ndk; the init_hook adds the Android Rust targets
```

You also need:

- An Android SDK, including an NDK. `devbox.json` expects it in `~/Library/Android/sdk` on macOS and `~/Android` on Linux. Point `local.properties` at it with `sdk.dir=...`.

### Build and run

The Kotlin bindings and the `.so` libraries are generated and git-ignored. Run all the steps below after a fresh clone and after every change in `noir_spartan/`. The bindings and the `.so` files must come from the same build, otherwise UniFFI fails with a checksum mismatch when the app starts.

```bash
devbox run rust_build      # host build; uniffi-bindgen reads the API from this library (.dylib on macOS, .so on Linux); the first build is slow
devbox run gen_bindings    # → app/src/main/java/.../uniffi/noir_spartan/noir_spartan.kt
devbox run ndk_build       # → app/src/main/jniLibs/arm64-v8a/libnoir_spartan.so (+ x86_64/ on x86_64 hosts)
devbox run build_apk       # ./gradlew :app:assembleDebug (Gradle does not build the Rust code)
```

To run on an emulator (same ABI as the host) or a device (arm64-v8a):

```bash
devbox run emulator        # or emulator-headless
devbox run install_apk
devbox run launch_app
```

Look at `devbox.json` for the exact commands behind each script, for example to run them without devbox.

### Circuits

The submodule does not contain the compiled `target/` artifacts, so run `devbox run compile_circuits` (see [One-time setup](#one-time-setup)) before building the APK. Without them the app finds no circuits. To compile by hand, or to run the backend on its own, see `app/src/main/assets/spartan-backend/README.md`. The short version: from `app/src/main/assets/spartan-backend/`, `devbox run build` builds the backend and `devbox run start` compiles the circuits and runs them. Circuits are compiled with the `nargo-t256` fork of Noir (tag `t256-v0.26-1`).

## Known limitations

- The submodule and the `spartan-backend` dependency in `noir_spartan/Cargo.toml` both point at the tag `v1.0.0`, but they are updated separately. If they drift apart, the circuit assets may not match the prover.
- Only the two benchmark circuits appear in the app. Other circuits in the submodule are exercised through the standalone backend.
- The app does not preserve its state: after the app is killed, or after leaving the Benchmarks page, you have to precompute or benchmark again.

## Benchmarks

Measured on a **Fairphone 4** (Snapdragon 750G) on 2026-09-29, with 5 runs per step. The table shows the
average of two sessions in ms. Every time includes loading the circuit files from flash, see
[What the timings include](#using-the-app). The raw log is
[`benchmarks/FairPhone4/zkp.log`](benchmarks/FairPhone4/zkp.log).

| Step | `c0200_swiyu_jwt` | `c0202_sicpa_backend_constant` |
|------|------------------:|-------------------------------:|
| Proof generation (monolithic) | 11 866 | 15 472 |
| Precomputation (offline, once) | 18 523 | 18 709 |
| Proof generation (precomputed, online) | 9 481 | 11 854 |
| Proof verification | 8 493 | 10 007 |

The log predates the `precompute.bin` load time / online proving time split (`load_ms`, `prove_ms`), so the
precomputed row still includes reading `precompute.bin`. It was recorded with spartan-backend commit `d6b2e20`,
which has the same prover code as `v1.0.0`.

To reproduce the log file, execute in the project root

```bash
./gradlew installDebug
```

to build the Android app.

Make sure that your device is connected and run

```bash
adb logcat > "$(date -I).log"
```

to capture the log output. Finally, execute in a separate shell

```
adb shell am start -n com.example.zkp_android_spartan/.MainActivity
```

to load the Android app to the device and run it. You can then execute any desired benchmarks manually.

To extract the relevant logs, use

```bash
grep -E 'ZKP|noir_spartan' $(date -I).log > benchmarks/<device>/zkp.log
```

and can then use the resulting log file for further processing (e.g. `grep -vE  'spartan_backend::precompute|circuit_instance|vega_prover' benchmarks/<device>/zkp.log` to get the only logs related to the benchmarks directly).

## License

Mozilla Public License 2.0, see [LICENSE](LICENSE).
