package com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Banking Wallet — Modular Monolith (Phase 1).
 *
 * <p>All bounded contexts ({@code identity}, {@code wallet}, {@code ledger}, {@code transaction})
 * live as packages within this single application. Cross-context communication uses in-process
 * events via {@code @TransactionalEventListener}.
 *
 * <p>Package boundaries are enforced by ArchUnit tests — contexts may only import each other's
 * {@code *.api} sub-packages.
 *
 * @see org.springframework.boot.autoconfigure.EnableAutoConfiguration
 */
@SpringBootApplication
@EnableScheduling
public class WalletApplication {

  public static void main(String[] args) {
    SpringApplication.run(WalletApplication.class, args);
  }
}
