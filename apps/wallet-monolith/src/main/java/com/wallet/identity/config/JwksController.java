package com.wallet.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

  private final RSAPublicKey publicKey;
  private final String keyId;

  public JwksController(RSAPublicKey publicKey, String keyId) {
    this.publicKey = publicKey;
    this.keyId = keyId;
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> keys() {
    RSAKey jwk = new RSAKey.Builder(publicKey).keyID(keyId).build();
    return new JWKSet(jwk).toJSONObject();
  }
}
