plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.pitest)
}

dependencies {
    // Spring Boot starters
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // Database
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Observability
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.logstash.logback.encoder)

    // OpenAPI
    implementation(libs.springdoc.openapi.starter)

    // Security - password hashing
    implementation(libs.argon2.jvm)
    implementation(libs.bouncycastle)

    // Internal libraries
    implementation(project(":libs:money"))

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jqwik)

    // PIT mutation testing — JUnit 5 support is opt-in
    pitest("org.pitest:pitest-junit5-plugin:1.2.1")
}

// ── PIT mutation testing ─────────────────────────────────────────
// Run manually: ./gradlew :apps:wallet-monolith:pitest
// Reports land in build/reports/pitest. Not part of `test` because PIT runs the whole suite per
// mutant — typically minutes to tens of minutes.
pitest {
    targetClasses.set(
        listOf("com.wallet.wallet.*", "com.wallet.ledger.*")
    )
    excludedClasses.set(
        listOf(
            // JPA entities — getters/setters are not interesting to mutate, and Hibernate
            // metadata fights some mutators.
            "com.wallet.wallet.domain.Wallet",
            "com.wallet.wallet.domain.WalletHold",
            "com.wallet.wallet.domain.WalletCredit",
            "com.wallet.ledger.domain.Account",
            "com.wallet.ledger.domain.JournalEntry",
        )
    )
    targetTests.set(
        listOf("com.wallet.wallet.*", "com.wallet.ledger.*")
    )
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    junit5PluginVersion.set("1.2.1")
    // Initial gate. Bump toward 80% as coverage grows; below this, the build fails.
    mutationThreshold.set(60)
    coverageThreshold.set(70)
}
