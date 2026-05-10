package com.wallet.wallet.job;

import com.wallet.wallet.domain.HoldState;
import com.wallet.wallet.domain.WalletHold;
import com.wallet.wallet.repository.WalletHoldRepository;
import com.wallet.wallet.service.WalletService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpirySweeper {

  private static final Logger log = LoggerFactory.getLogger(HoldExpirySweeper.class);

  private final WalletHoldRepository walletHoldRepository;
  private final WalletService walletService;

  public HoldExpirySweeper(WalletHoldRepository walletHoldRepository, WalletService walletService) {
    this.walletHoldRepository = walletHoldRepository;
    this.walletService = walletService;
  }

  @Scheduled(fixedDelay = 30000) // Every 30 seconds
  public void sweepExpiredHolds() {
    List<WalletHold> expiredHolds =
        walletHoldRepository.findByStateAndExpiresAtBefore(HoldState.ACTIVE, Instant.now());

    for (WalletHold hold : expiredHolds) {
      try {
        log.warn("Releasing expired hold: {}", hold.getId());
        walletService.releaseHold(hold.getId());
      } catch (Exception e) {
        log.error("Failed to release expired hold: {}", hold.getId(), e);
      }
    }
  }
}
