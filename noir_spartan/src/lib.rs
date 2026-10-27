uniffi::setup_scaffolding!();

#[global_allocator]
static GLOBAL: mimalloc::MiMalloc = mimalloc::MiMalloc;

mod spartan;
pub use spartan::generate_proof;
