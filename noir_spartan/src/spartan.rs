use spartan_backend::{
    instantiate_circuit_from_dir,
    prove_circuit,
};

use std::path::PathBuf;

#[uniffi::export]
pub fn generate_proof(pwd: String) -> bool {
    let dir = PathBuf::from(pwd);
    let circuit = instantiate_circuit_from_dir(&dir);
    let proof = prove_circuit(&circuit);
    match proof {
        Ok(_) => true,
        Err(e) => {
            eprintln!("failed to generate proof: {}", e);
            false
        }
    }
}
