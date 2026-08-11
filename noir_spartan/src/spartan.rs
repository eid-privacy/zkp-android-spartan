use spartan_backend::{
    instantiate_circuit_from_dir,
    prove_circuit,
    proof_to_base64,
};

use std::path::PathBuf;

#[uniffi::export]
pub fn generate_proof(pwd: String) -> String {
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_circuit_from_dir(&dir);
    let result = prove_circuit(&circuit);
    match result {
        Ok(proof) => {
            proof_to_base64(&proof)
        },
        Err(e) => {
            eprintln!("failed to generate proof: {}", e);
            String::new()
        }
    }
}
