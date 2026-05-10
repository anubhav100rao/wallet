package com.wallet.identity.controller;

import com.wallet.identity.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthService.AuthResult> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.ok(authService.register(req.email(), req.password()));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthService.AuthResult> login(@Valid @RequestBody LoginRequest req) {
    return ResponseEntity.ok(authService.login(req.email(), req.password()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthService.AuthResult> refresh(@Valid @RequestBody RefreshRequest req) {
    return ResponseEntity.ok(authService.refresh(req.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
    authService.logout(req.refreshToken());
    return ResponseEntity.ok().build();
  }

  public record RegisterRequest(
      @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password) {}

  public record LoginRequest(
      @NotBlank @Email String email, @NotBlank @Size(min = 1, max = 128) String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}
}
