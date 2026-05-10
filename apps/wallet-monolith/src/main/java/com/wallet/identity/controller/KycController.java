package com.wallet.identity.controller;

import com.wallet.identity.domain.KycStatus;
import com.wallet.identity.service.KycService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kyc")
public class KycController {

  private final KycService kycService;

  public KycController(KycService kycService) {
    this.kycService = kycService;
  }

  @PostMapping("/submit")
  public ResponseEntity<KycResponse> submitKyc(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    KycStatus status = kycService.submitAndAutoVerify(userId);
    return ResponseEntity.ok(new KycResponse(status.name()));
  }

  public record KycResponse(String status) {}
}
