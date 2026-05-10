package com.wallet.identity.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyPairConfig {

  @Bean
  public KeyPair rsaKeyPair() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Bean
  public RSAPublicKey rsaPublicKey(KeyPair keyPair) {
    return (RSAPublicKey) keyPair.getPublic();
  }

  @Bean
  public RSAPrivateKey rsaPrivateKey(KeyPair keyPair) {
    return (RSAPrivateKey) keyPair.getPrivate();
  }

  @Bean
  public String keyId() {
    return UUID.randomUUID().toString();
  }
}
