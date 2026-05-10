# 2. Build Tool and Module Layout

Date: 2026-05-10

## Status

Accepted

## Context

The banking wallet is designed as a modular monolith (Phase 1) that will be mechanically split into microservices (Phase 2). The build tool must support multi-module projects cleanly, enforce module boundaries, and make extraction straightforward.

We need to distinguish between:
- **Runnable apps** (`apps/`) — Spring Boot applications that produce fat jars via `bootJar`.
- **Shared libraries** (`libs/`) — plain JARs consumed by apps, containing domain types (Money), shared filters (idempotency), and event POJOs.

## Decision

We use **Gradle with Kotlin DSL** (`build.gradle.kts`) and the built-in **version catalog** (`gradle/libs.versions.toml`).

### Module layout

```
/                           # Root project: 'banking-wallet'
├── apps/
│   └── wallet-monolith/    # Phase 1 monolith (bootJar)
├── libs/
│   └── money/              # Money/Currency value objects (plain jar)
├── gradle/
│   └── libs.versions.toml  # Central version catalog
├── settings.gradle.kts     # Includes all sub-projects
└── build.gradle.kts        # Root: common config, Spotless
```

### Conventions

- `apps/*` modules apply `org.springframework.boot` and `io.spring.dependency-management` plugins and produce a `bootJar`.
- `libs/*` modules set `bootJar.enabled = false` / `jar.enabled = true` — they are plain library JARs with no Spring Boot main class.
- All versions are declared in `libs.versions.toml` and referenced via `libs.<alias>`.
- Common configuration (Java 21 toolchain, Spotless, JUnit Platform) is applied in the root `subprojects {}` block.
- Each module may add its own dependencies but must not duplicate version declarations.

## Consequences

- **Positive:** Clean separation between runnable and library modules. Phase 2 extraction is mechanical: move a package into a new `apps/*` module, add its `build.gradle.kts`, and wire dependencies.
- **Positive:** Version catalog avoids version drift across modules.
- **Negative:** Gradle Kotlin DSL has a steeper learning curve than Groovy for newcomers. Acceptable given the long-term benefits.
- **Negative:** Multi-module builds are slower than single-module. Mitigated by Gradle's build cache and parallel execution.
