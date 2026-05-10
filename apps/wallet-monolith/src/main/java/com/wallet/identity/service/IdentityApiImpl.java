package com.wallet.identity.service;

import com.wallet.identity.api.IdentityApi;
import com.wallet.identity.domain.KycStatus;
import com.wallet.identity.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityApiImpl implements IdentityApi {

  private final UserRepository userRepository;

  public IdentityApiImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isKycVerified(UUID userId) {
    return userRepository
        .findById(userId)
        .map(user -> user.getKycStatus() == KycStatus.VERIFIED)
        .orElse(false);
  }
}
