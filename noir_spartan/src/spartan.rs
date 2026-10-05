use spartan_backend::{
    instantiate_prover_circuit_from_dir,
    instantiate_verifier_circuit_from_dir,
    prove_circuit,
    proof_to_base64,
    verify_circuit_from_base64,
    run_precompute,
    precompute,
};

use std::path::PathBuf;
use std::sync::OnceLock;
use std::time::Instant;
use tracing_subscriber::fmt::MakeWriter;

struct LogcatBridge;

impl<'a> MakeWriter<'a> for LogcatBridge {
    type Writer = LogcatBridgeWriter;

    fn make_writer(&'a self) -> Self::Writer {
        LogcatBridgeWriter
    }
}

struct LogcatBridgeWriter;

impl std::io::Write for LogcatBridgeWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let text = String::from_utf8_lossy(buf);
        let line = text.trim_end();
        if !line.is_empty() {
            #[cfg(target_os = "android")]
            log::info!("{line}");
            #[cfg(not(target_os = "android"))]
            println!("{line}");
        }
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

static INIT_LOGGING: OnceLock<()> = OnceLock::new();

fn init_logging() {
    INIT_LOGGING.get_or_init(|| {
        #[cfg(target_os = "android")]
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("noir_spartan"),
        );
        let subscriber = tracing_subscriber::fmt()
            .without_time()
            .with_ansi(false)
            .with_max_level(tracing::Level::INFO)
            .with_writer(LogcatBridge)
            .finish();

        // Install only the tracing global default. `.init()` would also try to
        // install the tracing-log bridge as the global `log` logger, which
        // conflicts with android_logger above and panics with SetLoggerError(()).
        let _ = tracing::subscriber::set_global_default(subscriber);
    });
}

#[uniffi::export]
pub fn precompute_proof(pwd: String) -> String {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_prover_circuit_from_dir(&dir);
    let result = run_precompute(&circuit);
    match result {
        Ok(path) => {
            path.to_str().map_or_else(String::new, |s| s.to_string())
        },
        Err(e) => {
            tracing::error!("failed to precompute proof: {}", e);
            String::new()
        }
    }
}

#[uniffi::export]
pub fn generate_proof(pwd: String) -> String {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_prover_circuit_from_dir(&dir);
    let result = prove_circuit(&circuit);
    match result {
        Ok(proof) => {
            proof_to_base64(&proof)
        },
        Err(e) => {
            tracing::error!("failed to generate proof: {}", e);
            String::new()
        }
    }
}

/// Result of [`generate_proof_precomputed`]. `proof` is empty on failure.
#[derive(uniffi::Record)]
pub struct PrecomputedProof {
    pub proof: String,
    /// Time to read and decode `precompute.bin`, in milliseconds.
    pub load_ms: f64,
    /// Time of the online proving phase, in milliseconds.
    pub prove_ms: f64,
}

/// Same as `spartan_backend::prove_with_precompute`, but measures the
/// `precompute.bin` load and the online proving separately.
#[uniffi::export]
pub fn generate_proof_precomputed(pwd: String, path: String) -> PrecomputedProof {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_prover_circuit_from_dir(&dir);
    let path = PathBuf::from(path);

    let t_load = Instant::now();
    let loaded = precompute::load(&circuit, path.clone());
    let load_elapsed = t_load.elapsed();
    tracing::info!(
        "{}: read {} from disk in {:.3?}",
        circuit.name,
        path.display(),
        load_elapsed
    );
    let load_ms = load_elapsed.as_secs_f64() * 1000.0;

    let Some(mut prover) = loaded else {
        tracing::error!("{}: no precomputed prover available", circuit.name);
        return PrecomputedProof { proof: String::new(), load_ms, prove_ms: 0.0 };
    };

    let t_prove = Instant::now();
    let result = prover.prove_online(&circuit);
    let prove_ms = t_prove.elapsed().as_secs_f64() * 1000.0;
    match result {
        Ok(proof) => PrecomputedProof { proof: proof_to_base64(&proof), load_ms, prove_ms },
        Err(e) => {
            tracing::error!("failed to generate proof: {:?}", e);
            PrecomputedProof { proof: String::new(), load_ms, prove_ms }
        }
    }
}

#[uniffi::export]
pub fn verify(pwd: String, proof: String) -> bool {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_verifier_circuit_from_dir(&dir);
    let result = verify_circuit_from_base64(&circuit, &proof);
    match result {
        Ok(_) => {
            true
        }
        Err(e) => {
            tracing::error!("failed to verify proof: {}", e);
            false
        }
    }
}
