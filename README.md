# zkp-android-spartan

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.23694719.svg)](https://doi.org/10.5281/zenodo.23694719)

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

The final numbers are in the
[MS4 report](https://github.com/eid-privacy/zkp-reports/releases/latest/download/eid-privacy-MS4.pdf),
section *Benchmarks* (table "Benchmarking our circuits for age-proof"). They were measured on a **Samsung Galaxy A54**
(Android 16, 8 GB RAM) with circuit `c0200_swiyu_jwt`:

| Prover | Galaxy A54 |
|--------|-----------:|
| Spartan (monolithic) | 11.0 s |
| Spartan, pre-computed (online phase) | 4.5 s (load 0.4 s, final 3.4 s) |
| Pre-computation (offline, once per credential) | about 18 s |

To measure on your own device, build and install the app (see [Building](#building)) and open the **Benchmarks**
page. The app times every call itself, and Rust reports the `precompute.bin` load time and the online proving time
separately. The results appear on the page and in Logcat under the tag `ZKP`:

```bash
devbox run logcat -s ZKP
```

## How to cite

Archived on Zenodo: [10.5281/zenodo.23694719](https://doi.org/10.5281/zenodo.23694719) (this DOI always resolves to the latest version).
GitHub's "Cite this repository" button gives APA and BibTeX, from [CITATION.cff](CITATION.cff).

## License

Mozilla Public License 2.0, see [LICENSE](LICENSE).
