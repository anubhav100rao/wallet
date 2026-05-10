package com.wallet.identity.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wallet.identity.domain.User;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final RSAPrivateKey privateKey;
  private final String keyId;

  // 5 minutes TTL for access tokens
  private static final long ACCESS_TOKEN_TTL_MINUTES = 5;

  public JwtService(RSAPrivateKey privateKey, String keyId) {
    this.privateKey = privateKey;
    this.keyId = keyId;
  }

  public String generateAccessToken(User user) {
    try {
      JWSSigner signer = new RSASSASigner(privateKey);

      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build();

      Instant now = Instant.now();
      Instant expirationTime = now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);

      JWTClaimsSet claimsSet =
          new JWTClaimsSet.Builder()
              .subject(user.getId().toString())
              .claim("email", user.getEmail())
              .claim("roles", user.getRoles())
              .claim("tokenVersion", user.getTokenVersion())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(expirationTime))
              .build();

      SignedJWT signedJWT = new SignedJWT(header, claimsSet);
      signedJWT.sign(signer);

      return signedJWT.serialize();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate JWT", e);
    }
  }
}
