package com.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.repository.WalletCreditRepository;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import com.wallet.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Hammer the wallet with concurrent placeHold + transfer-shaped operations on a single source row
 * and assert correctness invariants hold:
 *
 * <ul>
 *   <li>No negative balance.
 *   <li>Available always equals total minus the sum of {@code ACTIVE} holds.
 *   <li>Exactly N successful holds when the wallet has exactly N×amount available.
 * </ul>
 *
 * <p>Default strategy is PESSIMISTIC. To run against the optimistic path, change the property to
 * {@code optimistic} in {@code @TestPropertySource} (or invoke the test class with {@code
 * -Dwallet.locking.strategy=optimistic}). Both must produce identical correctness outcomes;
 * throughput differs.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "wallet.locking.strategy=pessimistic")
class WalletConcurrencyTest {

  private static final int CONCURRENT_OPS = 1000;
  private static final BigDecimal AMOUNT_PER_OP = new BigDecimal("1.0000");
  private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.0000");

  @Autowired private WalletService walletService;
  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletHoldRepository walletHoldRepository;
  @Autowired private WalletCreditRepository walletCreditRepository;

  @BeforeEach
  void setUp() {
    walletCreditRepository.deleteAllInBatch();
    walletHoldRepository.deleteAllInBatch();
    walletRepository.deleteAllInBatch();
  }

  @Test
  @Timeout(120)
  void thousandConcurrentHoldsOnHotWalletPreserveCorrectness() throws Exception {
    UUID walletId = seedWallet(STARTING_BALANCE);

    ExecutorService pool = Executors.newFixedThreadPool(64);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(CONCURRENT_OPS);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();
    ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < CONCURRENT_OPS; i++) {
      pool.submit(
          () -> {
            try {
              start.await();
              try {
                walletService.placeHold(walletId, UUID.randomUUID(), AMOUNT_PER_OP);
                successes.incrementAndGet();
              } catch (com.wallet.wallet.domain.InsufficientFundsException e) {
                insufficient.incrementAndGet();
              } catch (Throwable t) {
                unexpected.add(t);
              }
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }

    start.countDown();
    done.await();
    pool.shutdownNow();

    if (!unexpected.isEmpty()) {
      throw new AssertionError(
          "Unexpected exceptions during concurrent run: " + unexpected.peek(), unexpected.peek());
    }

    // Wallet was seeded with exactly STARTING_BALANCE/AMOUNT_PER_OP capacity.
    int expectedSuccesses = STARTING_BALANCE.divide(AMOUNT_PER_OP).intValue();
    assertThat(successes.get())
        .as("exactly STARTING_BALANCE/AMOUNT successes (no overspend, no lost holds)")
        .isEqualTo(expectedSuccesses);
    assertThat(insufficient.get()).isEqualTo(CONCURRENT_OPS - expectedSuccesses);

    // Final state: no negatives; total is unchanged (no captures yet); available is zero.
    Wallet w = walletRepository.findById(walletId).orElseThrow();
    assertThat(w.getTotalBalance()).isEqualByComparingTo(STARTING_BALANCE);
    assertThat(w.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);

    // Hold invariant must still hold.
    WalletInvariants.assertHoldInvariant(walletRepository, walletHoldRepository, walletId);

    // Exactly expectedSuccesses holds in ACTIVE state.
    long activeHolds =
        walletHoldRepository.findByWalletIdAndState(walletId, HoldState.ACTIVE).size();
    assertThat(activeHolds).isEqualTo(expectedSuccesses);
  }

  private UUID seedWallet(BigDecimal balance) {
    Wallet w = new Wallet(UUID.randomUUID(), "USD");
    w.creditTotalAndAvailable(balance);
    return walletRepository.save(w).getId();
  }
}
