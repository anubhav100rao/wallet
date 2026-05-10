package com.wallet.wallet.repository;

import com.wallet.wallet.domain.WalletCredit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletCreditRepository extends JpaRepository<WalletCredit, UUID> {
  boolean existsByWalletIdAndTransactionId(UUID walletId, UUID transactionId);
}
