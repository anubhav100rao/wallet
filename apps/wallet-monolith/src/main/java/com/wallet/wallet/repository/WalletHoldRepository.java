package com.wallet.wallet.repository;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.WalletHold;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletHoldRepository extends JpaRepository<WalletHold, UUID> {

  Optional<WalletHold> findByWalletIdAndTransactionId(UUID walletId, UUID transactionId);

  List<WalletHold> findByStateAndExpiresAtBefore(HoldState state, Instant time);

  List<WalletHold> findByWalletIdAndState(UUID walletId, HoldState state);
}
