# Banking Wallet — Build Checklist

A task-level, phase-organized checklist for building the wallet platform described in [product.md](product.md). Designed for an empty repo, learning-focused but production-grade, with all infra running in Docker.

Conventions:
- `[ ]` task to do, `[x]` done.
- **🎓 Learn:** = concept to internalize before/while implementing — write a paragraph in your notes once you can explain it cold.
- **✅ Done when:** = acceptance criteria. If you can't tick all of them, the task isn't done.
- Money is always `BigDecimal` (or minor-unit `BIGINT`). If you see `double` or `float` anywhere near money, it's a bug.

> **Scope reality-check.** Top-to-bottom, this is realistically **6–12 months of full-time senior work**. As a learning project, that's gold — but most of the conceptual payoff lives in **Phase 1 alone** (modular monolith with the full transfer saga, ledger, idempotency, observability, tests). Treat Phase 1 as a complete vertical slice you could ship and stop at; Phases 2–4 are independent capstone projects layered on top. If you only ever do Phase 1, you've still learned ~80% of the hard ideas. The phase exit criteria are designed so each phase stands alone.

---

## Phase 0 — Repo & toolchain bootstrap

### 0.1 Repository scaffolding
- [ ] `git init`, add `.gitignore` (Java, Gradle, IntelliJ, macOS, `.env`, `*.log`, `target/`, `build/`, `infra/docker-compose.override.yml`).
- [ ] Top-level `README.md` with a one-paragraph overview and link to `docs/product.md`.
- [ ] Create directory layout:
  ```
  /apps          # one folder per Spring Boot service (later phases)
  /libs          # shared libraries (money, events, idempotency)
  /infra         # docker-compose, k8s manifests, grafana dashboards
  /docs          # product.md, todo.md, ADRs
  /scripts       # bootstrap, seed, smoke-test scripts
  ```
- [ ] Decide build tool: **Gradle (Kotlin DSL)** recommended for multi-module. Initialize root `settings.gradle.kts` + `build.gradle.kts` with version catalog (`libs.versions.toml`).
- [ ] Pin Java 21 via `.tool-versions` (asdf) or `.sdkmanrc` (sdkman).
- [ ] Add `.editorconfig` and Spotless plugin (Google Java Format).
- [ ] Add pre-commit hook: `./gradlew spotlessCheck` + `./gradlew test --offline -x integrationTest`.
- [ ] Add `docs/adr/0001-record-architecture-decisions.md` (ADR template) — every cross-cutting decision below should produce an ADR.

✅ Done when: `./gradlew build` runs on a clean clone, pre-commit blocks an unformatted file.

### 0.2 Local infra in Docker
- [ ] `infra/docker-compose.yml` with:
  - `postgres:16` (single instance for local). Phase 1 monolith uses **one database with one schema per bounded context** (`identity`, `wallet`, `ledger`, `transaction`, `shared`) — matches the ArchUnit boundaries and makes the Phase 2 promotion to database-per-service mechanical. Phase 2 splits each schema into its own logical database in the same container; Phase 4 may further split into separate clusters. Pick this evolution path explicitly in an ADR.
  - `redis:7`.
  - `redpanda` (lighter than full Kafka for local) exposing 9092 + schema-registry-compatible port.
  - `mailhog` for email.
  - `prometheus`, `grafana`, `tempo`, `loki`, `otel-collector`.
  - `pgadmin` and `redpanda-console` for inspection.
- [ ] Named volumes for postgres + redpanda data; `healthcheck:` blocks for every service.
- [ ] `infra/docker-compose.override.yml` example for resource limits / port overrides.
- [ ] `scripts/dev-up.sh` and `scripts/dev-down.sh` wrappers.
- [ ] `scripts/seed-postgres.sql` — creates one DB per planned service so Phase 2 splits don't need data migration.
- [ ] Document required ports in `infra/README.md`.

✅ Done when: `./scripts/dev-up.sh` brings up the whole stack, `docker compose ps` shows all healthy, Grafana loads at `localhost:3000`.

🎓 **Learn:** why each piece is here. If you can't explain why Tempo is separate from Loki, read OpenTelemetry signal types.

---

## Phase 1 — Modular monolith

Single Spring Boot app, all domains as packages. No Kafka. In-process events via `@TransactionalEventListener`. This phase teaches ~60% of Spring Boot.

### 1.1 Application skeleton
- [ ] `apps/wallet-monolith` Spring Boot 3.x app, Java 21, virtual threads enabled (`spring.threads.virtual.enabled=true`).
- [ ] Dependencies: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`, `oauth2-resource-server`, `flyway-core`, `postgresql`, `micrometer-registry-prometheus`, `springdoc-openapi`.
- [ ] Package layout (one root package per bounded context):
  ```
  com.wallet.identity
  com.wallet.transaction
  com.wallet.wallet
  com.wallet.ledger
  com.wallet.shared       # Money, Idempotency, errors, events
  ```
- [ ] ArchUnit tests forbidding cross-context imports except via `*.api` sub-packages (this is what makes splitting in Phase 2 cheap).
- [ ] Actuator: expose `health`, `info`, `metrics`, `prometheus` only.
- [ ] Spring profiles: `local`, `test`, `prod` — plus a `dev` profile loaded by Docker Compose.

✅ Done when: `./gradlew :apps:wallet-monolith:bootRun --args='--spring.profiles.active=local'` boots green against the Docker stack.

🎓 **Learn:** what `@SpringBootApplication` actually expands to (`@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`). Read the `META-INF/spring/...AutoConfiguration.imports` mechanism.

### 1.2 Shared library: `Money` and `Currency`
- [ ] `Money` value object: `BigDecimal amount` + `Currency currency`. Immutable, `add`/`subtract`/`negate`/`multiply(BigDecimal)`.
- [ ] Currency mismatch throws `CurrencyMismatchException` (a domain exception, not `IllegalArgumentException`).
- [ ] Static factories: `Money.of(BigDecimal, Currency)`, `Money.zero(Currency)`, `Money.minor(long minor, Currency)`.
- [ ] JPA `AttributeConverter` storing as `(amount NUMERIC(19,4), currency CHAR(3))` — never one column.
- [ ] Jackson serializer/deserializer producing `{ "amount": "100.00", "currency": "INR" }` (string, not number — JSON loses precision on big decimals in some clients).
- [ ] Property-based tests with jqwik: `a + b == b + a`, `a + zero == a`, `a + (-a) == zero`, no operation produces a `Double`.

✅ Done when: every service that handles money uses `Money` and the JSON contract is round-trippable.

🎓 **Learn:** why `BigDecimal.equals` and `compareTo` differ. Why `new BigDecimal(0.1)` is the wrong way to construct one.

### 1.3 Shared library: idempotency
- [ ] Migration: `idempotency_keys(key TEXT PK, request_hash TEXT, response_status INT, response_body JSONB, created_at TIMESTAMPTZ)`.
- [ ] `@Idempotent` filter or interceptor on mutating endpoints reading `Idempotency-Key` header.
- [ ] On duplicate key + matching hash → replay stored response. On duplicate key + mismatched hash → `409 Conflict` with `idempotency_key_reused_with_different_payload`.
- [ ] TTL job purging keys older than 24h (configurable).
- [ ] Integration test: 100 concurrent identical requests produce one row + 99 replays.

✅ Done when: the same `Idempotency-Key` with the same body produces one effect and identical responses; with a different body, returns 409.

🎓 **Learn:** why idempotency keys are about *client retries*, not *server deduplication*. Read Stripe's idempotency docs.

### 1.4 Identity context
- [ ] Migration: `users(id UUID PK, email CITEXT UNIQUE, password_hash TEXT, kyc_status TEXT, created_at)`, `roles`, `user_roles`, `refresh_tokens(id, user_id, token_hash, expires_at, revoked_at)`.
- [ ] Argon2id (preferred) or bcrypt password hashing via `PasswordEncoder`.
- [ ] `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`.
- [ ] JWT signed with **RS256**; key pair generated at startup for `local`, loaded from secret for `prod`. Expose JWKS at `/.well-known/jwks.json`.
- [ ] Refresh token rotation: each refresh issues a new refresh token and revokes the old one; reuse of a revoked token revokes the whole family (detection of token theft).
- [ ] **Access-token revocation strategy** (decide and write the ADR before coding — wallet platforms cannot ship without one):
  - **Option A — short TTL, no blacklist:** access tokens valid for ≤ 5 minutes; refresh tokens carry the revocation power. Simple, no per-request lookup, but worst-case window of 5 min after KYC revocation/password change.
  - **Option B — Redis blacklist:** every protected request checks a Redis set keyed by `jti` (token id) or `userId+iat`. On password change / KYC revoke / admin lockout, push to Redis with TTL = remaining token lifetime. Adds 1 Redis hit per request; gives instant revocation.
  - **Option C — token version on user:** include `tokenVersion` claim; bump it on revocation; resource server compares against a cached user record. Cheaper than blacklist if you already cache users.
  - Recommendation for this project: **A + C** — 5-min TTL plus a `tokenVersion` claim, no Redis blacklist, since refresh rotation already handles long-tail compromise.
- [ ] KYC stub: `POST /kyc/submit` flips status to `PENDING` then `VERIFIED` after a fake delay. Real KYC is out of scope.
- [ ] `@TransactionalEventListener` emits `UserRegistered`, `UserKycVerified` for in-process consumers.
- [ ] Spring Security config: `oauth2ResourceServer().jwt()` validating against the local JWKS.

✅ Done when: a user can register, log in, refresh, and the JWT validates on a protected endpoint.

🎓 **Learn:** why refresh-token rotation + reuse detection beats long-lived tokens. Why JWT in a cookie ≠ JWT in `Authorization` header for CSRF.

### 1.5 Ledger context (do this BEFORE wallet — ledger is truth)
- [ ] Migrations:
  - `accounts(id UUID PK, owner_user_id UUID, type TEXT, currency CHAR(3), created_at)` — types: `USER_CASH`, `SYSTEM_CASH`, `FEE_INCOME`, `SUSPENSE`.
  - `journal_entries(id UUID PK, transaction_id UUID, account_id UUID, direction CHAR(1) CHECK IN ('D','C'), amount NUMERIC(19,4), currency CHAR(3), posted_at TIMESTAMPTZ, metadata JSONB)`.
  - Constraint: `journal_entries` is **append-only**. Enforce in the database: create a dedicated Postgres role `ledger_writer` with `INSERT, SELECT` only on `journal_entries`; the app's ledger DataSource connects as this role. The default `app_user` does not even have those grants. (Application-level guards are not enough — a bug or a compromised app should still not be able to mutate the ledger.)
- [ ] `LedgerService.post(transactionId, List<Entry>)` — validates entries sum to zero per currency, all entries written in one tx.
- [ ] **Pick one representation and apply it everywhere** (write the ADR before coding):
  - **Option A — direction + unsigned amount** (kept above): balance is `SUM(CASE direction WHEN 'D' THEN -amount ELSE amount END)`; the per-transaction zero-sum invariant is `SUM(CASE direction WHEN 'D' THEN -amount ELSE amount END) = 0`; raw `SUM(amount)` is meaningless.
  - **Option B — signed amount, no direction column**: `amount NUMERIC(19,4)` (negative = debit, positive = credit), banned from being zero via check constraint; balance is `SUM(amount)`; the zero-sum invariant is `SUM(amount) = 0`. Simpler, but loses the explicit accounting-style D/C label.
- [ ] Reversing-entry helper: `reverse(transactionId)` posts opposite entries with `metadata.reverses = transactionId`.
- [ ] Test: a thousand random transactions; per-account balance always matches the running signed sum; the **signed** sum across all entries is always zero (per transaction and globally).

✅ Done when: you cannot post an unbalanced journal (signed sum per `transaction_id` must be zero, enforced by a deferred constraint or service-level check); you cannot UPDATE or DELETE a row even from psql as the app user; the signed sum across the whole system is zero after any number of operations.

🎓 **Learn:** why double-entry's invariant (∑ = 0) is what makes distributed bookkeeping tractable. Read Square's "Books" blog post or Martin Kleppmann on event sourcing.

### 1.6 Wallet context
- [ ] Migration: `wallets(id UUID PK, user_id UUID, currency CHAR(3), total_balance NUMERIC(19,4), available_balance NUMERIC(19,4), version BIGINT, created_at, updated_at, UNIQUE(user_id, currency))`.
- [ ] Migration: `wallet_holds(id UUID PK, wallet_id UUID, transaction_id UUID, amount NUMERIC(19,4), state TEXT CHECK IN ('ACTIVE','RELEASED','CAPTURED'), created_at, expires_at)`.
- [ ] `WalletService.placeHold(walletId, txId, amount)` — single tx, `SELECT … FOR UPDATE` on wallet, check `available_balance >= amount`, decrement `available_balance`, insert hold row in `ACTIVE`. Idempotent on `(walletId, txId)`.
- [ ] `WalletService.releaseHold(holdId)` — single tx, increment `available_balance` back, mark hold `RELEASED`. Idempotent (no-op if already `RELEASED`).
- [ ] `WalletService.capture(holdId)` — single tx, decrement source wallet's `total_balance` by the held amount, mark hold `CAPTURED`. (Available is already decremented by `placeHold`, so it is not touched here.) Idempotent (no-op if already `CAPTURED`).
- [ ] `WalletService.credit(walletId, txId, amount)` — single tx, increment destination wallet's both `total_balance` and `available_balance`. Idempotent on `(walletId, txId)` via a `wallet_credits` dedupe table.
- [ ] **Settlement contract:** a transfer is `SETTLED` only after **both** `capture(sourceHoldId)` and `credit(destinationWalletId, txId, amount)` have committed. Wallet-service exposes them as separate operations; transaction-service is responsible for invoking both and treating the pair as one settlement step (see saga below).
- [ ] Implement **both** locking strategies behind a feature flag and benchmark them under contention:
  - Pessimistic: `SELECT … FOR UPDATE` (`PESSIMISTIC_WRITE`).
  - Optimistic: `@Version` with retry on `OptimisticLockingFailureException`.
- [ ] Concurrency test: 1000 concurrent transfers from the same source wallet — final balance is correct, no negative balances, no lost holds.
- [ ] **Hold expiry sweeper:** `@Scheduled` job (every 30s) finds `ACTIVE` holds with `expires_at < now()` and calls `releaseHold(holdId)` (idempotent). Default hold TTL: 15 minutes from `placeHold` — long enough for any sane saga, short enough that a stalled saga doesn't lock funds indefinitely. Emit `wallet.hold_expired` (a `wallet.hold_released` with `metadata.reason = 'expired'`) so the saga can detect and abort.
- [ ] **Hold invariant:** `total_balance - available_balance == SUM(amount) WHERE state = 'ACTIVE'` for every wallet at all times. Assert it after every wallet op in tests; the reconciliation job (Phase 3.2) checks it nightly across the fleet.
- [ ] Read API: `GET /wallets/{id}` (balances), `GET /wallets/{id}/holds?state=ACTIVE` (debugging stuck holds). Authz: owner only.

✅ Done when: contention test passes for both locking modes, hold expiry sweeper releases an artificially-stuck hold within one tick, and the hold invariant is asserted in every wallet test.

🎓 **Learn:** read-committed vs repeatable-read in Postgres. Why `SELECT … FOR UPDATE` is per-row, not per-table. When optimistic locking outperforms pessimistic (low contention) and when it collapses (high contention).

### 1.7 Transaction context (the saga, in-process flavor)
- [ ] Migrations: `transactions(id UUID PK, type TEXT, state TEXT, idempotency_key TEXT, from_wallet_id UUID, to_wallet_id UUID, amount NUMERIC(19,4), currency CHAR(3), created_at, updated_at)`.
- [ ] State machine: `PENDING → HELD → POSTED → SETTLED` and compensations `→ COMPENSATED`. Implement first as a hand-rolled enum + transitions table; *optionally* refactor to Spring Statemachine after to compare.
- [ ] `POST /transfers` — validates idempotency, creates `PENDING` row, emits in-process `TransferRequested` event.
- [ ] In-process listeners (synchronous for Phase 1, but each listener owns its own tx so the seams stay obvious for Phase 2):
  1. `TransferRequested` → `WalletService.placeHold(source)` → emits `wallet.hold_placed` → saga advances `PENDING → HELD`.
  2. `wallet.hold_placed` → `LedgerService.post([source -100, destination +100])` → emits `ledger.posted` → saga advances `HELD → POSTED`.
  3. `ledger.posted` → `WalletService.capture(sourceHold)` + `WalletService.credit(destination, txId, amount)` → emits `wallet.captured` + `wallet.credited` → saga advances `POSTED → SETTLED`, emits `transfer.completed`.
- [ ] State semantics are crisp: `HELD` = funds reserved on source, `POSTED` = books are correct (ledger is now truth), `SETTLED` = wallet cache also reflects the books on both sides. A transfer that lands in `POSTED` but fails to settle is recoverable by replaying capture+credit (idempotent), not by reversing.
- [ ] Failure paths: any step before `POSTED` emits `wallet.hold_released` / `transfer.failed` and the saga moves to `COMPENSATED`. After `POSTED`, you do **not** roll back — you post a *reversing* ledger entry and emit a separate `transfer.reversed` saga.
- [ ] `POST /deposits` (external → user wallet): no source hold (external is not a wallet); `WalletService.credit(destination, txId, amount)` + ledger entries `(SYSTEM_CASH -100, user +100)`. Saga states: `PENDING → POSTED → SETTLED`. No `HELD` because there is nothing to hold against.
- [ ] `POST /withdrawals` (user wallet → external): full hold flow on source. Saga: `placeHold(source) → HELD → ledger.post([source -100, SYSTEM_CASH +100]) → POSTED → capture(sourceHold) → SETTLED`. No destination wallet to credit (external payout is handled by a separate payout integration, out of scope here).
- [ ] Internal transfer (the canonical flow above) is the only saga that uses **both** `capture` and `credit`. Codify these three flows as separate saga definitions, not one polymorphic blob.
- [ ] OpenAPI spec generated, served at `/swagger-ui.html`.

✅ Done when: the canonical `$100 Alice → Bob` flow works, transitions through every state, and a forced failure at each step lands in `COMPENSATED` with the ledger showing reversal.

🎓 **Learn:** the saga pattern (orchestration vs choreography) and why 2PC doesn't work across services. Why you reverse, not roll back, in accounting.

### 1.8 Outbox table (groundwork for Phase 2)
- [ ] Migration: `outbox_events(id UUID PK, aggregate_type TEXT, aggregate_id UUID, event_type TEXT, payload JSONB, headers JSONB, created_at, published_at NULL, attempts INT DEFAULT 0)`.
- [ ] Every domain event is *also* written to outbox in the same tx as the state change — even though Phase 1 has no Kafka, the discipline is what matters.
- [ ] Scheduled poller (`@Scheduled`) that logs unpublished events (no broker yet). Phase 2 swaps the log for Kafka.

✅ Done when: every business event has a corresponding outbox row and the poller picks it up.

🎓 **Learn:** the "dual write" problem. Why `kafkaTemplate.send()` from inside `@Transactional` is broken in subtle ways.

### 1.9 Cross-cutting: errors, validation, observability
- [ ] `@RestControllerAdvice` with RFC 7807 `application/problem+json` responses.
- [ ] Domain exceptions → 4xx, infra exceptions → 5xx, with explicit mapping table tested.
- [ ] Bean Validation (`@Valid`, `@NotNull`, `@Positive`) on all DTOs.
- [ ] Micrometer: counters/timers on every saga transition, every wallet op, every ledger post.
- [ ] Structured JSON logging with `logstash-logback-encoder`, `traceId`/`spanId` in every line.
- [ ] OpenTelemetry auto-instrumentation, traces shipping to Tempo via OTLP.
- [ ] Grafana dashboard: TPS, p50/p95/p99 latency per endpoint, saga state distribution, ledger imbalance alarm (must always be 0).

✅ Done when: a `$100 Alice → Bob` request shows up as one trace spanning all internal calls, with metrics in Grafana and structured logs in Loki.

### 1.10 Testing strategy
- [ ] Unit tests for `Money`, ledger invariants, state-machine transitions.
- [ ] `@DataJpaTest` with Testcontainers Postgres for repository tests.
- [ ] `@SpringBootTest` with full Testcontainers stack for end-to-end saga tests.
- [ ] Contract tests (Spring Cloud Contract or hand-rolled) for every public REST endpoint.
- [ ] Mutation testing with PIT on `wallet` and `ledger` packages — score >= 80%.
- [ ] Load test with k6 or Gatling: 1k transfers/sec sustained for 5 minutes, no errors, no imbalance.

✅ Done when: CI runs the whole suite (incl. integration) on every PR in <10 min, mutation score gate enforced.

### 1.11 Phase 1 — Spring Boot learning outcomes

After Phase 1 you should be able to explain each of these unaided and point to the place in your own code where it shows up. If you can't, the phase isn't done.

**Boot, configuration, lifecycle**
- What `@SpringBootApplication` expands to and how `META-INF/spring/...AutoConfiguration.imports` drives auto-configuration.
- The role of starters (`spring-boot-starter-*`) and how to read a starter's BOM.
- Profiles (`local`, `test`, `dev`, `prod`), `application-{profile}.yml`, profile activation precedence.
- Externalized config: `@ConfigurationProperties` (typed, validated) vs `@Value` (avoid except for trivia).
- Virtual threads (`spring.threads.virtual.enabled=true`): what changes for Tomcat/JDBC, what doesn't.
- Graceful shutdown (`server.shutdown=graceful`) and `lifecycle.timeout-per-shutdown-phase`.

**Dependency injection & beans**
- Constructor injection vs field injection (and why field injection is banned in this codebase).
- `@Component` / `@Service` / `@Repository` / `@Configuration` — same wiring, different intent.
- `@Conditional*`, `@Profile`, `@ConditionalOnProperty` for environment-specific beans.
- Bean scopes (`singleton`, `prototype`, `request`) and when each is correct.

**Web & validation**
- Spring MVC request flow: `DispatcherServlet` → `HandlerMapping` → `HandlerAdapter` → `Controller` → `HttpMessageConverter`.
- `@RestController`, content negotiation, custom Jackson modules.
- Bean Validation 3.0: `@Valid`, `@Validated`, `@NotNull`, `@Positive`, custom constraints, `MethodValidationPostProcessor`.
- `@RestControllerAdvice` + `ProblemDetail` (RFC 7807) for consistent error responses.
- OpenAPI generation via springdoc and how it maps `@RequestBody`/`@Schema`.

**Persistence**
- Spring Data JPA repositories: derived queries, `@Query`, projections (interface + DTO), pagination.
- `@Entity` lifecycle, `EntityManager` flush modes, the first-level cache.
- `AttributeConverter` for `Money`/Currency (and why composite types are usually clearer than a single converter).
- `@Transactional` propagation (`REQUIRED`, `REQUIRES_NEW`, `NESTED`) and isolation (`READ_COMMITTED`, `REPEATABLE_READ`).
- Why self-invocation of `@Transactional` methods is silently broken (and how to detect it).
- `@Lock(PESSIMISTIC_WRITE)` vs `@Version` optimistic locking — implementation, exceptions, retry semantics.
- Flyway migration ordering, repeatable scripts (`R__`), baseline-on-migrate.

**Events (in-process)**
- `ApplicationEventPublisher`, `@EventListener`, `@TransactionalEventListener` phases (`BEFORE_COMMIT`, `AFTER_COMMIT`, `AFTER_ROLLBACK`).
- Why `AFTER_COMMIT` is the right phase for "fire only if state actually persisted".

**Security**
- Spring Security 6 lambda DSL: `SecurityFilterChain` bean, `authorizeHttpRequests`, method security via `@PreAuthorize`.
- `oauth2ResourceServer().jwt()` with a local JWKS endpoint.
- `PasswordEncoder` abstractions; why Argon2id > bcrypt > PBKDF2 in 2026.
- CSRF: when to disable (stateless API with `Authorization` header) and when not (cookie-bearing apps).

**Observability**
- Spring Boot Actuator endpoints, exposure (`management.endpoints.web.exposure.include`), why you don't expose `/env` and `/heapdump` in prod.
- Micrometer `MeterRegistry`, counter/timer/gauge, `@Timed`, custom tags.
- Structured logging with `logstash-logback-encoder` and the MDC `traceId`/`spanId` keys.
- OpenTelemetry auto-instrumentation via the Java agent or the SDK starter.

**Scheduling**
- `@EnableScheduling`, `@Scheduled(fixedDelay/fixedRate/cron)`, `TaskScheduler` configuration, why `fixedDelay` is safer than `fixedRate` for jobs that can run long.
- ShedLock for "run only on one instance".

**Testing**
- The Spring Test slice annotations: `@SpringBootTest` (full context), `@DataJpaTest`, `@WebMvcTest`, `@JsonTest` — when each is the right tool.
- `@MockBean` / `@SpyBean` and the cost of context restarts (`@DirtiesContext`).
- Testcontainers `@Container` + `@ServiceConnection` (Spring Boot 3.1+) for zero-config DB/Kafka in tests.
- AssertJ, jqwik (property-based), PIT (mutation testing) — what each catches that the others miss.
- Contract testing with Spring Cloud Contract.

**Other**
- Why `RestTemplate` is in maintenance mode and `RestClient` (Boot 3.2+) is the new sync HTTP client.
- Build plugins: Spring Boot Gradle plugin (`bootJar`, `bootBuildImage`), how layered jars enable better Docker caching.

### 1.12 Phase 1 exit criteria (don't move on until all are true)
- [ ] All endpoints idempotent and proven so by tests.
- [ ] Ledger invariant (signed sum = 0) is automatically asserted after every test.
- [ ] Hold invariant (`total - available = SUM(active holds)`) asserted after every wallet op test.
- [ ] You can draw the saga state machine on a whiteboard from memory.
- [ ] You can explain in one paragraph each: optimistic vs pessimistic locking, the dual-write problem, why ledgers are append-only, refresh-token rotation, access-token revocation strategy.
- [ ] An ADR exists for: build tool, locking strategy, currency storage format, password hashing algorithm, JWT signing algorithm, access-token revocation, money JSON format, ledger entry representation (Option A vs B), hold TTL.
- [ ] Postman/Bruno collection in `tests/manual/` covers register → login → deposit → transfer → withdraw → reconcile, runnable by `bruno run`.

---

## Phase 2 — Split into services

Extract `identity`, `wallet`, `ledger`, `transaction` into separate Spring Boot apps. Add Kafka, real outbox publishing, gateway, JWT propagation. The transfer flow becomes the actual saga.

### 2.1 Service extraction prep
- [ ] One Postgres DB per service (still one container; separate logical DBs). ADR: schema-per-service vs db-per-service vs cluster-per-service.
- [ ] Shared `libs/events` Gradle module: event POJOs + Jackson config. Versioned (`v1` package today, `v2` later). All services depend on this.
- [ ] Shared `libs/money` and `libs/idempotency` modules (extract from monolith).
- [ ] Decide event envelope schema: `{ id, type, version, occurredAt, traceId, payload }` — write the ADR.

### 2.2 Canonical event matrix
Before any Kafka code, lock the event vocabulary. Names use `<aggregate>.<verb>` style (matches product.md). Topic = aggregate; event type lives in the envelope `type` field. Compaction off (these are facts, not state); retention 14 days locally, 90+ in prod. Partition key = aggregate id (so all events for one transaction/wallet/account stay ordered on one partition).

- [ ] Document the matrix in `docs/events.md`:

| Topic | Event type | Producer | Consumers | Partition key | Idempotency key |
|---|---|---|---|---|---|
| `transaction.events` | `transfer.requested` | transaction-svc | audit-svc *(fact only — wallet acts on `wallet.place_hold_requested`, never on this)* | `transaction_id` | `transaction_id` |
| `transaction.events` | `transfer.completed` | transaction-svc | notification-svc, audit-svc | `transaction_id` | `transaction_id` |
| `transaction.events` | `transfer.failed` | transaction-svc | notification-svc, audit-svc | `transaction_id` | `transaction_id` |
| `transaction.events` | `transfer.reversed` | transaction-svc | notification-svc, audit-svc | `transaction_id` | `transaction_id` |
| `wallet.events` | `wallet.hold_placed` | wallet-svc | transaction-svc, audit-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.events` | `wallet.hold_released` | wallet-svc | transaction-svc, audit-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.events` | `wallet.captured` | wallet-svc | transaction-svc, audit-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.events` | `wallet.credited` | wallet-svc | transaction-svc, audit-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.commands` | `wallet.place_hold_requested` | transaction-svc | wallet-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.commands` | `wallet.release_hold_requested` | transaction-svc | wallet-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.commands` | `wallet.capture_requested` | transaction-svc | wallet-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `wallet.commands` | `wallet.credit_requested` | transaction-svc | wallet-svc | `wallet_id` | `(wallet_id, transaction_id)` |
| `ledger.commands` | `ledger.post_requested` | transaction-svc | ledger-svc | `transaction_id` | `transaction_id` |
| `ledger.events` | `ledger.posted` | ledger-svc | transaction-svc, audit-svc, reconciliation-svc | `transaction_id` | `transaction_id` |
| `identity.events` | `user.registered` | identity-svc | notification-svc, audit-svc | `user_id` | `user_id` (one per registration) |
| `identity.events` | `user.kyc_verified` | identity-svc | audit-svc | `user_id` | `(user_id, kyc_submission_id)` |

- [ ] Convention: a topic name ending in `.commands` carries imperative messages (one expected consumer, the owner of the aggregate); `.events` carries facts (any number of consumers, but **never** the aggregate owner — that would loop or double-process). The orchestrator (transaction-svc) is the only producer of `*.commands`; aggregate owners (wallet-svc, ledger-svc) only emit `*.events` and only consume `*.commands` directed at them.
- [ ] Each consumer group MUST treat its idempotency key as the deduplication primitive (table or Redis SETNX with TTL ≥ retention). At-least-once delivery + idempotent consumer = effectively-once.
- [ ] Add the matrix to the Phase 2 ADR; any new event added later requires a PR updating both the matrix and `docs/events.md`.

### 2.3 Kafka (Redpanda) wiring
- [ ] Create topics from `infra/topics.yml` (one entry per topic in the matrix above) via a Redpanda admin init container.
- [ ] Retry topic + DLQ **per consumer group** (e.g. `wallet.events.retry.transaction-svc`, `wallet.events.dlq.transaction-svc`) — retry topics are owned by the consumer, not the producer.
- [ ] Idempotent producer config (`enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection=5`, `retries=Integer.MAX_VALUE`).
- [ ] Schema registry (Redpanda's, or Apicurio) — start with JSON Schema (`BACKWARD` compatibility enforced in CI), plan migration to Avro in Phase 4.
- [ ] Per-service `application.yml` `spring.kafka` config: bootstrap, consumer group, deserializers, error handler with retry topic + DLQ.
- [ ] **Tracing wire-up:** configure `KafkaTemplate` and `@KafkaListener` containers with the Micrometer `ObservationRegistry` so trace context propagates via Kafka headers (`traceparent`). Acceptance: a manually-injected request at the gateway produces one continuous trace through gateway → transaction-svc → Kafka → wallet-svc → Kafka → ledger-svc → Kafka → notification-svc, with no orphan spans in Tempo.

### 2.4 Outbox publisher
- [ ] Each service's `outbox_events` is polled by a `@Scheduled(fixedDelay=...)` job inside that service.
- [ ] Polled rows: claim with `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100`, publish to Kafka, mark `published_at`.
- [ ] Backoff + `attempts` increment on send failure; alert when `attempts > 5`.
- [ ] Test: kill the broker mid-batch, restart — every row published exactly once on the retry (at-least-once + downstream idempotency = effectively-once).
- [ ] **Ordering caveat:** `SKIP LOCKED` + parallel pollers do not preserve global outbox order. Acceptable here because the only ordering that matters is *per-aggregate*, and Kafka preserves order *within a partition* once partition key = aggregate id. Within a single transaction's outbox row set, sequence is preserved by polling rows for the same `aggregate_id` together (`ORDER BY aggregate_id, created_at` and only releasing a batch when all of an aggregate's pending rows are publishable). Document this in the ADR.
- [ ] **Validate consumer idempotency tables**: schema is `(consumer_group, idempotency_key, processed_at) PRIMARY KEY (consumer_group, idempotency_key)`. Even though partition key + group rebalance guarantees only-one-instance-per-key in steady state, the table guards against rebalances duplicating work mid-flight and against replay scenarios.

🎓 **Learn:** why `SKIP LOCKED` exists. Why "exactly once" over the wire is impossible and you instead build "at-least-once delivery + idempotent consumers". The difference between *global ordering*, *per-partition ordering*, and *per-aggregate ordering* — production systems guarantee the third.

### 2.5 `identity-service` (extracted)
- [ ] Move identity package into `apps/identity-service`. Own DB.
- [ ] Publish `user.registered`, `user.kyc_verified` via outbox.
- [ ] Expose JWKS endpoint (other services validate tokens against it).
- [ ] Healthchecks + readiness probes.

### 2.6 `wallet-service` (extracted)
- [ ] Move wallet package into `apps/wallet-service`. Own DB.
- [ ] Consume `wallet.commands` (`wallet.place_hold_requested`, `wallet.release_hold_requested`, `wallet.capture_requested`, `wallet.credit_requested`); each handler is idempotent on `(wallet_id, transaction_id)`.
- [ ] Publish `wallet.hold_placed`, `wallet.hold_released`, `wallet.captured`, `wallet.credited` on `wallet.events` via outbox.
- [ ] Distributed lock via Redisson on `wallet:{id}` to reduce contention across instances. **Postgres row lock remains the correctness mechanism**; Redis is purely for throughput.
- [ ] Handle lock-expiry-before-completion: every wallet op is a Postgres tx; Redis lock is an optimization, not a guard.

🎓 **Learn:** the Martin Kleppmann vs Antirez "Redlock" debate. Why Redis distributed locks are not safe alone for correctness.

### 2.7 `ledger-service` (extracted)
- [ ] Move ledger package into `apps/ledger-service`. Own DB.
- [ ] Consume `ledger.post_requested`, write entries + outbox in one tx, publish `ledger.posted`.
- [ ] Reject any duplicate `transactionId` (uniqueness constraint) — consumer is idempotent.
- [ ] Read API: `GET /accounts/{id}/balance`, `GET /accounts/{id}/entries?from=&to=`.

### 2.8 `transaction-service` (extracted, the orchestrator)
- [ ] Move transaction/saga package into `apps/transaction-service`. Own DB (transactions, idempotency_keys, outbox).
- [ ] Saga becomes Kafka-driven: consume `wallet.*` and `ledger.*`, advance state, emit next command.
- [ ] Saga step persistence: every state transition is a row update in `transactions` + an outbox row, in one tx.
- [ ] Timeout handler: a `@Scheduled` job that finds sagas stuck in a state for > N minutes and emits a compensation. Specifically, sagas stuck in `HELD` for > 10 minutes emit `wallet.release_hold_requested` and transition to `COMPENSATED`. This is the second line of defense; the wallet-service hold-expiry sweeper (1.6) is the first — both must converge on the same outcome.
- [ ] Read API: `GET /transactions/{id}` returning current state and history.

✅ Done when: the canonical $100 transfer flows over Kafka through 4 services and lands in `SETTLED`. Killing any single service mid-flow leaves the saga resumable on restart.

### 2.9 `spring-cloud-gateway`
- [ ] `apps/edge-gateway` Spring Cloud Gateway app.
- [ ] Routes: `/auth/**` → identity, `/transfers|deposits|withdrawals|transactions/**` → transaction, `/wallets/**` → wallet (read-only), `/accounts/**` → ledger (read-only).
- [ ] Global filters: trace-id propagation, JWT validation against JWKS, rate limiting via Redis (`RequestRateLimiter`).
- [ ] CORS config sourced from env.

### 2.10 Service-to-service auth
- [ ] Per-service principal: each service has its own client credential, gets a service token from identity (or a static signed JWT for local).
- [ ] Internal endpoints validate **two** identities: end-user JWT (passed through) + caller-service identity.
- [ ] mTLS between services in Kubernetes (defer to Phase 4) — for Phase 2, signed service tokens are acceptable.
- [ ] ADR documenting the model.

### 2.11 Resilience4j
- [ ] Circuit breakers around outbound HTTP calls (gateway → services, service → service if any sync calls remain).
- [ ] Retries with exponential backoff + jitter on Kafka consumers (with retry topic, not in-thread retry storms).
- [ ] Bulkheads on downstream-call thread pools.
- [ ] Dashboards for breaker state in Grafana.

### 2.12 Service discovery
- [ ] **Pick one** — Eureka, Consul, or "we run on Kubernetes so we use DNS". Recommendation: skip Eureka/Consul entirely if you'll deploy to k8s in Phase 4; use Docker Compose service names locally.
- [ ] ADR explaining the choice.

### 2.13 Phase 2 — Spring Boot learning outcomes

After Phase 2 you should have hands-on fluency with the parts of Spring that exist *because* of distribution.

**Multi-module & shared libraries**
- Multi-module Gradle layout: a root build, an `apps/*` for runnable Boot apps, a `libs/*` for plain library jars (no `bootJar`).
- Why a library module sets `bootJar.enabled = false` and `jar.enabled = true`, and what the Spring Boot dependency-management plugin gives you regardless.
- Internal versioning of shared event POJOs (`v1`/`v2` packages, no breaking changes within a major).

**Spring Cloud Gateway**
- Reactive (Netty) under the hood — implications: no blocking calls on the main route, no `RestTemplate`.
- Route definition via Java DSL vs YAML; pros of each.
- Built-in filters: `RewritePath`, `AddRequestHeader`, `CircuitBreaker`, `RequestRateLimiter` (Redis-backed).
- Global filters for cross-cutting concerns (trace propagation, JWT extraction).

**Spring Kafka**
- `KafkaTemplate` with idempotent producer config; `ProducerListener` for delivery callbacks.
- `@KafkaListener` and the underlying `ConcurrentMessageListenerContainer`; concurrency vs partitions.
- Container ack modes: `RECORD`, `BATCH`, `MANUAL`, `MANUAL_IMMEDIATE` — when each is correct.
- `DefaultErrorHandler`, retry backoff, `DeadLetterPublishingRecoverer`, retry-topic patterns (`@RetryableTopic`).
- Manual offset commit semantics and the at-least-once contract.
- `RecordInterceptor` / `ProducerInterceptor` for tracing and metrics.
- Kafka header propagation and how Micrometer Tracing populates `traceparent` automatically.

**Outbox pattern in Spring**
- Why a `@Scheduled` poller using `JdbcTemplate` + `SELECT … FOR UPDATE SKIP LOCKED` is the simplest correct outbox.
- The dual-write problem and why `kafkaTemplate.send()` inside `@Transactional` is wrong.
- Per-aggregate ordering: how partition key + outbox grouping by aggregate guarantees what you actually need.

**Resilience4j**
- The Spring Boot starter and `@CircuitBreaker` / `@Retry` / `@Bulkhead` / `@RateLimiter` annotations; combinator order matters.
- Functional API vs annotation API.
- Metrics integration with Micrometer; the breaker-state Grafana dashboard.

**HTTP clients**
- `RestClient` (sync) for service-to-service calls when you must remain sync; configuring it with `BufferingClientHttpRequestFactory`, interceptors, error decoders.
- `WebClient` (async) when you actually need async/streaming.
- Why HTTP service-to-service calls are minimized in this architecture (events instead).

**Distributed tracing**
- `ObservationRegistry`, `Observation` API, and how `@Observed` produces both metrics and spans.
- Tracing through Kafka: `ObservationRegistry` configured on `KafkaTemplate` and listener container.
- B3 vs W3C Trace Context — Boot 3 defaults to W3C.

**Distributed locking**
- ShedLock for scheduled tasks (one-runner-per-cluster).
- Redisson `RLock` for application-level locking; the lease/renewal pattern; why Redis locks are not safe alone.

**Multi-DataSource / multi-Postgres**
- Configuring multiple `DataSource` beans in services that talk to two DBs (rare here, but useful — `@ConfigurationProperties("spring.datasource.foo")`, `JpaTransactionManager` per source, qualifier-based wiring).

**Service-to-service auth in Boot**
- Resource server with multiple `JwtDecoder`s (one for end-user, one for service tokens) — `JwtIssuerAuthenticationManagerResolver`.
- `OAuth2AuthorizedClientManager` if you go client-credentials for service identity.

**Build & packaging**
- `bootBuildImage` (Cloud Native Buildpacks) producing OCI images without a Dockerfile.
- Layered jars and the resulting Docker layer cache wins.

### 2.14 Phase 2 exit criteria
- [ ] Killing any single service mid-saga is recoverable.
- [ ] Rolling-restarting the broker doesn't lose or duplicate effects.
- [ ] You can articulate the difference between sync request/reply and event-driven flow, and why this domain prefers the latter.
- [ ] All ADRs from 2.x written.

---

## Phase 3 — Supporting services

### 3.1 `notification-service`
- [ ] New Spring Boot app. No DB needed (or a small dedupe table).
- [ ] `@KafkaListener` consuming `transaction.events` (`transfer.completed`, `transfer.failed`, `transfer.reversed`) and `wallet.events` if needed for finer-grained user notifications.
- [ ] Manual ack mode; commit only after the email/push side-effect succeeds.
- [ ] Retry topic + DLQ; replay tooling (`/admin/dlq/replay/{topic}`).
- [ ] Email via Mailhog (local) / SES (prod) behind a `MailSender` interface.
- [ ] Push via FCM behind a `PushSender` interface; provide a `NoopPushSender` for local.
- [ ] Template engine (Thymeleaf or Pebble) for email bodies.
- [ ] Dedupe: `(event_id, channel)` unique table, since at-least-once delivery means the listener may see the same event twice.

🎓 **Learn:** Spring Kafka container ack modes (`RECORD`, `BATCH`, `MANUAL`, `MANUAL_IMMEDIATE`). When each is correct.

### 3.2 `reconciliation-service` (Spring Batch)
- [ ] New Spring Boot app. Read replica or API access to ledger and wallet.
- [ ] Batch job:
  - `Step 1`: `ItemReader` over ledger entries for the day, chunked by account.
  - `Step 2`: compare each account's signed ledger sum against the wallet's `total_balance`. Also assert the hold invariant: `total_balance - available_balance == SUM(amount) WHERE state='ACTIVE'` for every wallet.
  - `Step 3`: write `reconciliation_reports(date, account_id, ledger_sum, wallet_balance, holds_sum, drift_kind, drift)` — `drift_kind` distinguishes ledger/wallet drift from hold-invariant drift.
  - `Step 4`: if any drift of either kind, emit `reconciliation.drift_detected` and PagerDuty/email alert.
- [ ] Job is **restartable**: chunk-oriented, last-committed offset persisted by Spring Batch's metadata tables.
- [ ] Cron via Spring `@Scheduled` (or a k8s `CronJob` later); job runs at 02:00 UTC.
- [ ] Test: corrupt one wallet balance manually, run the job, assert drift detected and alert emitted.

🎓 **Learn:** Spring Batch's `JobRepository`, why chunk-oriented processing is restartable, and the `Step`/`Tasklet`/`Chunk` distinction.

### 3.3 `audit-service`
- [ ] New Spring Boot app + Elasticsearch container in `infra/docker-compose.yml`.
- [ ] Consume **every** domain topic (one consumer group per service is overkill — one fan-out group is fine).
- [ ] Persist raw event in Postgres (`audit_events` table, append-only).
- [ ] Index searchable projection in Elasticsearch.
- [ ] Search API: `GET /audit?userId=&type=&from=&to=` with cursor pagination.
- [ ] Schema-evolution test: produce a v1 event, then a v2 (with extra field), assert both index correctly.

🎓 **Learn:** consumer group semantics. Schema evolution rules (backwards/forwards compatible).

### 3.4 Distributed tracing end-to-end
- [ ] Verify a single `traceId` flows: gateway → transaction-service → Kafka → wallet-service → Kafka → ledger-service → Kafka → notification-service.
- [ ] Kafka header propagation via `KafkaHeaderMapper` (Micrometer Tracing handles this if configured).
- [ ] Tempo dashboard with the canonical flow as a saved query.

### 3.5 Phase 3 — Spring Boot learning outcomes

After Phase 3 you should have first-hand experience with Spring's batch and integration surfaces.

**Spring Batch**
- Domain model: `Job` → `Step` → (`ItemReader`, `ItemProcessor`, `ItemWriter`) for chunk-oriented steps; `Tasklet` for arbitrary one-shot work.
- `JobRepository`, `JobLauncher`, `JobOperator` and the metadata tables (`BATCH_JOB_*`).
- Restartability semantics: why chunk size is a recovery unit, what `ExecutionContext` persists.
- Partitioned steps (`PartitionHandler`) for parallelism — and when it's overkill.
- Skip / retry policies; reading from JDBC vs Kafka vs files.
- Running batch jobs as a Kubernetes `CronJob` later.

**Spring Kafka — advanced**
- Consumer group rebalance protocols: eager vs cooperative-sticky; what changes for in-flight processing.
- Exactly-once semantics in Kafka (transactional producer + read-committed consumer) — and why we deliberately don't rely on it.
- `@RetryableTopic` for declarative retry-with-backoff topics + DLT.
- Replay tooling: an admin endpoint that re-publishes from DLT.
- Record-level dedupe with a "processed events" table keyed by `(consumer_group, idempotency_key)`.

**Spring Data Elasticsearch**
- `ElasticsearchOperations` vs `ElasticsearchRepository`.
- Index lifecycle: rolling indices, aliases, reindex strategies.
- Schema evolution mapped to event evolution: how a v2 event with a new field flows into the index without breaking v1 queries.

**Schema evolution**
- JSON Schema with backward/forward/full compatibility modes; CI gating new schemas.
- The mental model that lets you choose between adding a nullable field, deprecating a field, and a major version bump.

**Distributed tracing — across consumers**
- Verifying that a trace started at the gateway survives 5+ async hops.
- Saved Tempo queries for the canonical flow and how to wire them into Grafana dashboards.

### 3.6 Phase 3 exit criteria
- [ ] Reconciliation has caught a manually-introduced drift in a test.
- [ ] DLQ replay tooling has been used at least once.
- [ ] A trace for a single transfer spans 5+ services in Tempo.

---

## Phase 4 — Production polish

### 4.1 Schema registry + Avro
- [ ] Migrate event schemas from JSON to Avro.
- [ ] Schema registry (Apicurio or Confluent) compose service (Phase 0 already prepared the slot).
- [ ] CI step: every PR validates new schemas against `BACKWARD` compatibility.
- [ ] Producer/consumer codegen wired into Gradle.

### 4.2 Outbox via Debezium CDC
- [ ] Debezium connector container reading WAL from each service's Postgres.
- [ ] Replace the `@Scheduled` outbox poller with Debezium → Kafka Connect → topics.
- [ ] Compare: poller vs Debezium, document tradeoffs (latency, ops complexity, ordering guarantees).

🎓 **Learn:** Postgres logical replication, replication slots, why Debezium's outbox pattern uses an outbox table even though it's CDC.

### 4.3 Kubernetes deployment
- [ ] Helm chart per service in `infra/charts/`.
- [ ] `infra/charts/common` library chart for shared concerns (probes, resources, sidecars).
- [ ] ConfigMaps + Secrets (sourced from Sealed Secrets or external-secrets-operator).
- [ ] HorizontalPodAutoscaler on `transaction-service` and `wallet-service` (CPU + custom Kafka consumer-lag metric).
- [ ] PodDisruptionBudgets on stateful services.
- [ ] NetworkPolicies: only the gateway is reachable from outside the namespace.
- [ ] mTLS via service mesh (Linkerd preferred for simplicity, Istio if you want the full kit) — replaces the service-token model from Phase 2.
- [ ] Local k8s via `kind` or `colima` — `scripts/k8s-up.sh` brings up the full stack.

### 4.4 Chaos testing
- [ ] LitmusChaos or Chaos Mesh experiments:
  - Kill `wallet-service` mid-saga → saga resumes, no double-spend.
  - Network partition between `transaction-service` and Kafka → saga stalls, recovers.
  - Postgres failover → no data loss, brief unavailability acceptable.
- [ ] Each experiment has a documented expected outcome and an alert that should fire.

### 4.5 Load testing
- [ ] Gatling or k6 scenario in `tests/load/`:
  - Steady state: 5k transfers/sec, p99 < 500ms.
  - Spike: 0 → 20k in 30s, no errors.
  - Soak: 1k/sec for 6 hours, no memory leak, no Kafka lag growth.
- [ ] Capacity-planning notes in `docs/capacity.md`: TPS per service, breaking points, scaling levers.

### 4.6 Security hardening
- [ ] Run `./gradlew dependencyCheckAggregate` (OWASP) in CI; fail on CVSS ≥ 7.
- [ ] `trivy image` scan of every built image in CI.
- [ ] Secrets scanning with `gitleaks` pre-commit and in CI.
- [ ] Penetration-test the gateway: SQLi, XSS, JWT replay, IDOR on `/transactions/{id}`.
- [ ] Rate-limit auth endpoints separately (stricter).
- [ ] Audit log for every privilege-escalating action.

### 4.7 Operational readiness
- [ ] Runbooks in `docs/runbooks/`: "saga stuck in HELD", "ledger imbalance alarm", "DLQ growing", "Kafka consumer lag".
- [ ] On-call escalation matrix.
- [ ] Backup/restore tested for every Postgres DB; restore time documented.
- [ ] SLOs documented and Grafana SLO dashboard live.

### 4.8 Optional: WebFlux read API
- [ ] `apps/streaming-api` Spring WebFlux app.
- [ ] `GET /wallets/{id}/balance/stream` SSE stream of balance changes (consumes `wallet.events`).
- [ ] WebSocket variant for bidirectional clients.
- [ ] Backpressure and slow-consumer handling — drop oldest, not block producer.

🎓 **Learn:** when WebFlux is the right tool (high-fanout, long-lived connections) and when it isn't (CRUD).

### 4.9 Phase 4 — Spring Boot learning outcomes

After Phase 4 you should be able to take any Spring Boot service to production-grade on Kubernetes.

**Boot in Kubernetes**
- Liveness, readiness, and startup probes — what each means, why mixing them up causes restart loops.
- `management.endpoint.health.probes.enabled` and the `livenessstate`/`readinessstate` indicators; `AvailabilityChangeEvent` to flip readiness during graceful shutdown.
- Spring Boot's graceful shutdown phases and their interaction with k8s `terminationGracePeriodSeconds`.
- Buildpacks (`bootBuildImage`) producing reproducible OCI images; comparing to a hand-rolled Dockerfile.
- ConfigMap-backed config via Spring Cloud Kubernetes (or plain env vars) and the pros of each.
- Secrets via Sealed Secrets / external-secrets-operator + how `application.yml` references them.

**Schema registry & Avro**
- Avro `SpecificRecord` codegen wired into Gradle.
- `KafkaAvroSerializer` / `KafkaAvroDeserializer` and `specific.avro.reader=true`.
- Schema compatibility modes (`BACKWARD`, `FORWARD`, `FULL`) and what each prohibits at the producer/consumer.
- Migration story: dual-write JSON+Avro for a window, then flip readers.

**CDC with Debezium**
- Postgres logical replication, `wal_level=logical`, replication slots, publications.
- Debezium's outbox event router SMT (Single Message Transform) — the topic isn't `outbox`, it's whatever the SMT routes to.
- Trade-offs vs the polled outbox: lower latency, more ops surface (connector restart, slot bloat).

**Reactive Spring (optional, §4.8)**
- WebFlux: `RouterFunction` vs annotated controllers, `Mono`/`Flux`, `WebClient`.
- Reactor Kafka or Spring Cloud Stream's reactive binder for non-blocking consumers.
- SSE via `Flux<ServerSentEvent<>>`, WebSocket via `WebSocketHandler`; back-pressure strategies (`onBackpressureDrop`, `onBackpressureBuffer`).
- When WebFlux is the right tool (high-fanout streaming) and when it isn't (CRUD, blocking JDBC).

**Production polish**
- HPA with custom metrics from Micrometer (Kafka consumer lag) via Prometheus Adapter.
- PodDisruptionBudgets and how rolling restarts interact with consumer-group rebalances.
- Service mesh (Linkerd) replacing the service-token model — automatic mTLS, identity via SPIFFE IDs.
- Spring Boot's `info.*` keys for build/git metadata exposed via `/actuator/info`.

**Operational habits**
- Capturing thread dumps and heap dumps in production via Actuator (only behind auth!).
- `/actuator/loggers` to change log levels at runtime.
- Reading `/actuator/metrics/{name}` to debug a number Grafana doesn't show.

### 4.10 Phase 4 exit criteria
- [ ] One chaos experiment runs in CI on every release branch.
- [ ] You can deploy a new version of `wallet-service` with zero downtime and zero saga errors during the rollout.
- [ ] Every runbook has been used at least once in a tabletop exercise.

---

## Master decisions log (write the ADR before you write the code)

- [ ] ADR-0002: Build tool & module layout
- [ ] ADR-0003: Database-per-service vs schema-per-service
- [ ] ADR-0004: Event envelope schema
- [ ] ADR-0005: JSON vs Avro for events (and migration plan)
- [ ] ADR-0006: Money storage format (`NUMERIC(19,4)` vs minor-unit `BIGINT`)
- [ ] ADR-0007: JWT algorithm + key rotation strategy
- [ ] ADR-0008: Wallet locking strategy (pessimistic vs optimistic + when each)
- [ ] ADR-0009: Outbox poller vs Debezium CDC (and timeline)
- [ ] ADR-0010: Service discovery & service-to-service auth
- [ ] ADR-0011: Idempotency key TTL & reuse semantics
- [ ] ADR-0012: Reconciliation cadence & drift response
- [ ] ADR-0013: Access-token revocation strategy (TTL, blacklist, `tokenVersion`)
- [ ] ADR-0014: Wallet hold TTL & expiry policy (sweeper cadence, saga-level timeout)
- [ ] ADR-0015: Ledger entry representation (Option A direction+unsigned vs Option B signed)
- [ ] ADR-0016: Outbox ordering guarantees (per-aggregate vs global) and SKIP LOCKED tradeoff
- [ ] ADR-0017: Saga command vs event topology (orchestration via `*.commands` topics)

Each ADR: ~1 page. Status (`Proposed` / `Accepted` / `Superseded`), Context, Decision, Consequences. Read [Michael Nygard's original post](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) once before writing the first one.

---

## How to use this checklist

- Work top-down within a phase; don't jump ahead.
- A box gets ticked **only** when its `✅ Done when` clauses all pass — no partial credit.
- The 🎓 **Learn** items aren't optional: write a paragraph in your notes once you can explain the concept without referring back. If you can't, the implementation isn't really yours yet.
- When you discover a missing task, add it under the right section and date it. This file is living.
