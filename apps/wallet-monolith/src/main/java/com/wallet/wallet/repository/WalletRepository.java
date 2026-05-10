package com.wallet.wallet.repository;

import com.wallet.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

  // Pessimistic Locking strategy (ADR-0008)
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.id = :id")
  Optional<Wallet> findByIdForUpdate(UUID id);

  Optional<Wallet> findByUserIdAndCurrency(UUID userId, String currency);
}
