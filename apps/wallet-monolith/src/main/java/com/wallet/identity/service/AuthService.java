package com.wallet.identity.service;

import com.wallet.identity.domain.RefreshToken;
import com.wallet.identity.domain.User;
import com.wallet.identity.repository.RefreshTokenRepository;
import com.wallet.identity.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final org.springframework.context.ApplicationEventPublisher eventPublisher;

  private static final long REFRESH_TOKEN_TTL_DAYS = 30;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      org.springframework.context.ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public AuthResult register(String email, String rawPassword) {
    if (userRepository.findByEmail(email).isPresent()) {
      throw new IllegalArgumentException("Email already in use");
    }

    String hash = passwordEncoder.encode(rawPassword);
    User user = new User(email, hash);
    userRepository.save(user);

    eventPublisher.publishEvent(
        new com.wallet.identity.api.UserRegistered(
            user.getId(), user.getEmail(), user.getCreatedAt()));

    return login(email, rawPassword);
  }

  @Transactional
  public AuthResult login(String email, String rawPassword) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid credentials");
    }

    String accessToken = jwtService.generateAccessToken(user);

    // Generate secure random string for refresh token
    String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    String tokenHash = hashToken(rawRefreshToken);

    Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
    RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    return new AuthResult(accessToken, rawRefreshToken);
  }

  @Transactional(noRollbackFor = IllegalArgumentException.class)
  public AuthResult refresh(String rawRefreshToken) {
    String tokenHash = hashToken(rawRefreshToken);

    Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
    if (tokenOpt.isEmpty()) {
      throw new IllegalArgumentException("Invalid refresh token");
    }

    RefreshToken token = tokenOpt.get();
    User user = token.getUser();

    if (!token.isValid()) {
      // If a revoked token is used, this indicates theft! Revoke the entire family.
      if (token.getRevokedAt() != null) {
        refreshTokenRepository.revokeAllForUser(user.getId());
        user.revokeTokens(); // Increments tokenVersion, invalidating all existing JWTs
        userRepository.save(user);
      }
      throw new IllegalArgumentException("Invalid or expired refresh token");
    }

    // Token is valid. Rotate it.
    token.revoke();
    refreshTokenRepository.save(token);

    String accessToken = jwtService.generateAccessToken(user);

    // Issue new refresh token
    String newRawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
    String newTokenHash = hashToken(newRawRefreshToken);
    Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);

    RefreshToken newRefreshToken = new RefreshToken(user, newTokenHash, expiresAt);
    refreshTokenRepository.save(newRefreshToken);

    return new AuthResult(accessToken, newRawRefreshToken);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    String tokenHash = hashToken(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHash(tokenHash)
        .ifPresent(
            token -> {
              token.revoke();
              refreshTokenRepository.save(token);
            });
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  public record AuthResult(String accessToken, String refreshToken) {}
}
