rootProject.name = "banking-wallet"

// The version catalog 'libs' is automatically loaded from gradle/libs.versions.toml
// No explicit configuration needed — Gradle 8.x convention.

// Phase 1 modules
include("apps:wallet-monolith")
include("libs:money")
