plugins {
    `java-library`
}

// This is a plain library — no bootJar
// It will be consumed by apps/wallet-monolith

dependencies {
    // Jackson for serialization/deserialization
    api("com.fasterxml.jackson.core:jackson-databind:2.18.3")

    // JPA for @Embeddable (optional dependency — consumers provide the implementation)
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.2.0")

    // Testing — use BOM for version alignment
    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj.core)
    testImplementation(libs.jqwik)
}
