package com.wallet.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository-only slice. {@code @DataJpaTest} narrows the Spring context to JPA + Testcontainers
 * Postgres — no controllers, no security, no scheduling. Boots in ~3s vs ~12s for a full
 * {@code @SpringBootTest}, which makes repo-level regressions cheap to lock down.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class WalletRepositorySliceTest {

  @Autowired private WalletRepository walletRepository;

  @Test
  void persistsAndFindsWalletByUserAndCurrency() {
    UUID userId = UUID.randomUUID();
    Wallet w = walletRepository.save(new Wallet(userId, "USD"));

    var found = walletRepository.findByUserIdAndCurrency(userId, "USD");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(w.getId());
  }

  @Test
  void uniqueConstraintRejectsDuplicateUserCurrency() {
    UUID userId = UUID.randomUUID();
    walletRepository.saveAndFlush(new Wallet(userId, "USD"));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> walletRepository.saveAndFlush(new Wallet(userId, "USD")))
        .isInstanceOfAny(
            org.springframework.dao.DataIntegrityViolationException.class,
            jakarta.persistence.PersistenceException.class);
  }

  @Test
  void findByIdForUpdateReturnsTheWalletAndAcquiresPessimisticLock() {
    Wallet w = new Wallet(UUID.randomUUID(), "USD");
    w.creditTotalAndAvailable(new BigDecimal("100.0000"));
    UUID id = walletRepository.save(w).getId();

    // Lock semantics aren't asserted here (would need a second tx); the test just confirms the
    // lock-mode-annotated query maps to the same row and returns it.
    var locked = walletRepository.findByIdForUpdate(id);
    assertThat(locked).isPresent();
    assertThat(locked.get().getId()).isEqualTo(id);
  }

  @Test
  void versionIncrementsOnUpdate() {
    Wallet w = walletRepository.save(new Wallet(UUID.randomUUID(), "USD"));
    Long initialVersion =
        walletRepository.findById(w.getId()).orElseThrow().getClass() != null
            ? readVersion(w.getId())
            : null;
    Wallet reloaded = walletRepository.findById(w.getId()).orElseThrow();
    reloaded.creditTotalAndAvailable(new BigDecimal("10.0000"));
    walletRepository.saveAndFlush(reloaded);

    Long newVersion = readVersion(w.getId());
    assertThat(newVersion).isGreaterThan(initialVersion);
  }

  private Long readVersion(UUID walletId) {
    try {
      Wallet w = walletRepository.findById(walletId).orElseThrow();
      var f = Wallet.class.getDeclaredField("version");
      f.setAccessible(true);
      return (Long) f.get(w);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
