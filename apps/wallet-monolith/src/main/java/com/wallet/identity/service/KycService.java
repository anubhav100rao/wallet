package com.wallet.identity.service;

import com.wallet.identity.api.UserKycVerified;
import com.wallet.identity.domain.KycStatus;
import com.wallet.identity.domain.User;
import com.wallet.identity.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {

  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  public KycService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public KycStatus submitAndAutoVerify(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    user.markKycPending();
    user.verifyKyc();
    userRepository.save(user);

    eventPublisher.publishEvent(new UserKycVerified(user.getId(), Instant.now()));
    return user.getKycStatus();
  }
}
