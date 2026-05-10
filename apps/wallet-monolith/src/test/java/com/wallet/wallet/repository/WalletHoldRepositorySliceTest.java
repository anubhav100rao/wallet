package com.wallet.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletHold;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class WalletHoldRepositorySliceTest {

  @Autowired private WalletRepository walletRepository;
  @Autowired private WalletHoldRepository walletHoldRepository;

  @Test
  void findByWalletIdAndTransactionIdReturnsTheHold() {
    Wallet w = walletRepository.save(new Wallet(UUID.randomUUID(), "USD"));
    UUID txId = UUID.randomUUID();
    WalletHold hold =
        walletHoldRepository.save(
            new WalletHold(
                w, txId, new BigDecimal("10.0000"), Instant.now().plus(15, ChronoUnit.MINUTES)));

    var found = walletHoldRepository.findByWalletIdAndTransactionId(w.getId(), txId);
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(hold.getId());
  }

  @Test
  void findByStateAndExpiresAtBeforeFiltersExpiredOnly() {
    Wallet w = walletRepository.save(new Wallet(UUID.randomUUID(), "USD"));
    Instant now = Instant.now();
    walletHoldRepository.save(
        new WalletHold(
            w, UUID.randomUUID(), new BigDecimal("1.0000"), now.minus(1, ChronoUnit.MINUTES)));
    walletHoldRepository.save(
        new WalletHold(
            w, UUID.randomUUID(), new BigDecimal("1.0000"), now.plus(1, ChronoUnit.MINUTES)));

    var expired = walletHoldRepository.findByStateAndExpiresAtBefore(HoldState.ACTIVE, now);
    assertThat(expired).hasSize(1);
  }

  @Test
  void findByWalletIdAndStateScopesToTheWallet() {
    Wallet w1 = walletRepository.save(new Wallet(UUID.randomUUID(), "USD"));
    Wallet w2 = walletRepository.save(new Wallet(UUID.randomUUID(), "USD"));

    walletHoldRepository.save(
        new WalletHold(
            w1,
            UUID.randomUUID(),
            new BigDecimal("1.0000"),
            Instant.now().plus(10, ChronoUnit.MINUTES)));
    walletHoldRepository.save(
        new WalletHold(
            w2,
            UUID.randomUUID(),
            new BigDecimal("1.0000"),
            Instant.now().plus(10, ChronoUnit.MINUTES)));

    assertThat(walletHoldRepository.findByWalletIdAndState(w1.getId(), HoldState.ACTIVE))
        .hasSize(1);
    assertThat(walletHoldRepository.findByWalletIdAndState(w2.getId(), HoldState.ACTIVE))
        .hasSize(1);
  }
}
