use spartan_backend::{
    instantiate_circuit_from_dir,
    prove_circuit,
    proof_to_base64,
    verify_circuit_from_base64,
    run_precompute,
    prove_with_precompute,
};

use std::path::PathBuf;
use std::sync::OnceLock;
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
    let circuit = instantiate_circuit_from_dir(&dir);
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
    let circuit = instantiate_circuit_from_dir(&dir);
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

#[uniffi::export]
pub fn generate_proof_precomputed(pwd: String, path: String) -> String {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_circuit_from_dir(&dir);
    let result = prove_with_precompute(&circuit, PathBuf::from(path));
    match result {
        Ok(proof) => {
            proof
        },
        Err(e) => {
            tracing::error!("failed to generate proof: {}", e);
            String::new()
        }
    }
}

#[uniffi::export]
pub fn verify(pwd: String, proof: String) -> bool {
    init_logging();
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_circuit_from_dir(&dir);
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
