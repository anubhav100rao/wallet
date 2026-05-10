package com.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.UUID;

/** Assertion helpers for the wallet domain invariants. */
public final class WalletInvariants {

  private WalletInvariants() {}

  /**
   * Asserts {@code total_balance - available_balance == SUM(amount) WHERE state = 'ACTIVE'} for the
   * given wallet. This must hold after every wallet operation.
   */
  public static void assertHoldInvariant(
      WalletRepository walletRepo, WalletHoldRepository holdRepo, UUID walletId) {
    Wallet w = walletRepo.findById(walletId).orElseThrow();
    BigDecimal heldSum =
        holdRepo.findByWalletIdAndState(walletId, HoldState.ACTIVE).stream()
            .map(h -> h.getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal expected = w.getTotalBalance().subtract(w.getAvailableBalance());
    assertThat(expected)
        .as(
            "Hold invariant for wallet %s: total(%s) - available(%s) must equal active-holds-sum(%s)",
            walletId, w.getTotalBalance(), w.getAvailableBalance(), heldSum)
        .isEqualByComparingTo(heldSum);
    assertThat(w.getAvailableBalance())
        .as("available_balance must never go negative for wallet %s", walletId)
        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }
}
