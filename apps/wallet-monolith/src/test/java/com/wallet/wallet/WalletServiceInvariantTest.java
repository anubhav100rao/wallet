package com.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.InsufficientFundsException;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.repository.WalletCreditRepository;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.repository.WalletRepository;
import com.wallet.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises {@link WalletService} directly and asserts the hold invariant <code>
 * total - available == SUM(active holds)</code> after every operation.
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletServiceInvariantTest {

  @Autowired private WalletService walletService;
  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletHoldRepository holdRepository;
  @Autowired private WalletCreditRepository creditRepository;

  @BeforeEach
  void setUp() {
    creditRepository.deleteAllInBatch();
    holdRepository.deleteAllInBatch();
    walletRepository.deleteAllInBatch();
  }

  private UUID seedWallet(BigDecimal balance) {
    Wallet w = new Wallet(UUID.randomUUID(), "USD");
    if (balance.signum() > 0) {
      w.creditTotalAndAvailable(balance);
    }
    return walletRepository.save(w).getId();
  }

  @Test
  void invariantHoldsAfterPlaceAndReleaseHold() {
    UUID wid = seedWallet(new BigDecimal("1000.0000"));
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    UUID hold1 = walletService.placeHold(wid, UUID.randomUUID(), new BigDecimal("100.0000"));
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);
    assertThat(walletRepository.findById(wid).orElseThrow().getAvailableBalance())
        .isEqualByComparingTo("900");

    UUID hold2 = walletService.placeHold(wid, UUID.randomUUID(), new BigDecimal("250.0000"));
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    walletService.releaseHold(hold1);
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    walletService.releaseHold(hold2);
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    Wallet w = walletRepository.findById(wid).orElseThrow();
    assertThat(w.getTotalBalance()).isEqualByComparingTo("1000");
    assertThat(w.getAvailableBalance()).isEqualByComparingTo("1000");
  }

  @Test
  void invariantHoldsAfterCapture() {
    UUID wid = seedWallet(new BigDecimal("500.0000"));
    UUID holdId = walletService.placeHold(wid, UUID.randomUUID(), new BigDecimal("200.0000"));
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    walletService.capture(holdId);
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    Wallet w = walletRepository.findById(wid).orElseThrow();
    assertThat(w.getTotalBalance()).isEqualByComparingTo("300");
    assertThat(w.getAvailableBalance()).isEqualByComparingTo("300");
    assertThat(holdRepository.findById(holdId).orElseThrow().getState())
        .isEqualTo(HoldState.CAPTURED);
  }

  @Test
  void invariantHoldsAfterCredit() {
    UUID wid = seedWallet(BigDecimal.ZERO);
    walletService.credit(wid, UUID.randomUUID(), new BigDecimal("75.0000"));
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);

    Wallet w = walletRepository.findById(wid).orElseThrow();
    assertThat(w.getTotalBalance()).isEqualByComparingTo("75");
    assertThat(w.getAvailableBalance()).isEqualByComparingTo("75");
  }

  @Test
  void placeHoldRejectsInsufficientFunds() {
    UUID wid = seedWallet(new BigDecimal("50.0000"));
    assertThatThrownBy(
            () -> walletService.placeHold(wid, UUID.randomUUID(), new BigDecimal("100.0000")))
        .isInstanceOf(InsufficientFundsException.class);
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);
  }

  @Test
  void placeHoldIsIdempotentOnRepeatedTxId() {
    UUID wid = seedWallet(new BigDecimal("500.0000"));
    UUID txId = UUID.randomUUID();
    UUID hold1 = walletService.placeHold(wid, txId, new BigDecimal("100.0000"));
    UUID hold2 = walletService.placeHold(wid, txId, new BigDecimal("100.0000"));
    assertThat(hold1).isEqualTo(hold2);
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);
    assertThat(walletRepository.findById(wid).orElseThrow().getAvailableBalance())
        .isEqualByComparingTo("400");
  }

  @Test
  void creditIsIdempotentOnRepeatedTxId() {
    UUID wid = seedWallet(BigDecimal.ZERO);
    UUID txId = UUID.randomUUID();
    walletService.credit(wid, txId, new BigDecimal("100.0000"));
    walletService.credit(wid, txId, new BigDecimal("100.0000")); // no-op
    WalletInvariants.assertHoldInvariant(walletRepository, holdRepository, wid);
    assertThat(walletRepository.findById(wid).orElseThrow().getTotalBalance())
        .isEqualByComparingTo("100");
  }
}
