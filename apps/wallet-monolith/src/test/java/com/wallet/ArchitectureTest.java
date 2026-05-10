package com.wallet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ArchUnit tests enforcing bounded context boundaries.
 *
 * <p>Contexts may only depend on each other through their {@code *.api} sub-packages. Direct
 * imports of another context's internal classes are forbidden — this is what makes splitting into
 * separate services in Phase 2 mechanical rather than architectural.
 *
 * <p>The {@code com.wallet.shared} package is universally accessible (it contains Money,
 * idempotency, errors, events).
 */
@AnalyzeClasses(packages = "com.wallet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // ── Identity context isolation ─────────────────────────────────

  @ArchTest
  static final ArchRule identity_internals_not_accessed_by_wallet =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.wallet..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.identity.domain..",
              "com.wallet.identity.service..",
              "com.wallet.identity.repository..",
              "com.wallet.identity.controller..",
              "com.wallet.identity.config..",
              "com.wallet.identity.event..")
          .as("Wallet context must not access identity internals (use identity.api instead)");

  @ArchTest
  static final ArchRule identity_internals_not_accessed_by_transaction =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.transaction..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.identity.domain..",
              "com.wallet.identity.service..",
              "com.wallet.identity.repository..",
              "com.wallet.identity.controller..",
              "com.wallet.identity.config..",
              "com.wallet.identity.event..")
          .as("Transaction context must not access identity internals (use identity.api instead)");

  @ArchTest
  static final ArchRule identity_internals_not_accessed_by_ledger =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.ledger..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.identity.domain..",
              "com.wallet.identity.service..",
              "com.wallet.identity.repository..",
              "com.wallet.identity.controller..",
              "com.wallet.identity.config..",
              "com.wallet.identity.event..")
          .as("Ledger context must not access identity internals (use identity.api instead)");

  // ── Wallet context isolation ───────────────────────────────────

  @ArchTest
  static final ArchRule wallet_internals_not_accessed_by_identity =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.wallet.domain..",
              "com.wallet.wallet.service..",
              "com.wallet.wallet.repository..",
              "com.wallet.wallet.controller..",
              "com.wallet.wallet.config..",
              "com.wallet.wallet.event..")
          .as("Identity context must not access wallet internals (use wallet.api instead)");

  @ArchTest
  static final ArchRule wallet_internals_not_accessed_by_ledger =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.ledger..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.wallet.domain..",
              "com.wallet.wallet.service..",
              "com.wallet.wallet.repository..",
              "com.wallet.wallet.controller..",
              "com.wallet.wallet.config..",
              "com.wallet.wallet.event..")
          .as("Ledger context must not access wallet internals (use wallet.api instead)");

  // ── Ledger context isolation ───────────────────────────────────

  @ArchTest
  static final ArchRule ledger_internals_not_accessed_by_identity =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.ledger.domain..",
              "com.wallet.ledger.service..",
              "com.wallet.ledger.repository..",
              "com.wallet.ledger.controller..",
              "com.wallet.ledger.config..",
              "com.wallet.ledger.event..")
          .as("Identity context must not access ledger internals (use ledger.api instead)");

  @ArchTest
  static final ArchRule ledger_internals_not_accessed_by_wallet =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.wallet..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.ledger.domain..",
              "com.wallet.ledger.service..",
              "com.wallet.ledger.repository..",
              "com.wallet.ledger.controller..",
              "com.wallet.ledger.config..",
              "com.wallet.ledger.event..")
          .as("Wallet context must not access ledger internals (use ledger.api instead)");

  // ── Transaction context isolation ──────────────────────────────

  @ArchTest
  static final ArchRule transaction_internals_not_accessed_by_identity =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.identity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.transaction.domain..",
              "com.wallet.transaction.service..",
              "com.wallet.transaction.repository..",
              "com.wallet.transaction.controller..",
              "com.wallet.transaction.config..",
              "com.wallet.transaction.event..",
              "com.wallet.transaction.listener..")
          .as(
              "Identity context must not access transaction internals (use transaction.api instead)");

  @ArchTest
  static final ArchRule transaction_internals_not_accessed_by_wallet =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.wallet..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.transaction.domain..",
              "com.wallet.transaction.service..",
              "com.wallet.transaction.repository..",
              "com.wallet.transaction.controller..",
              "com.wallet.transaction.config..",
              "com.wallet.transaction.event..",
              "com.wallet.transaction.listener..")
          .as("Wallet context must not access transaction internals (use transaction.api instead)");

  @ArchTest
  static final ArchRule transaction_internals_not_accessed_by_ledger =
      noClasses()
          .that()
          .resideInAPackage("com.wallet.ledger..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.wallet.transaction.domain..",
              "com.wallet.transaction.service..",
              "com.wallet.transaction.repository..",
              "com.wallet.transaction.controller..",
              "com.wallet.transaction.config..",
              "com.wallet.transaction.event..",
              "com.wallet.transaction.listener..")
          .as("Ledger context must not access transaction internals (use transaction.api instead)");
}
