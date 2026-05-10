package com.wallet.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.identity.controller.AuthController.LoginRequest;
import com.wallet.identity.controller.AuthController.RefreshRequest;
import com.wallet.identity.controller.AuthController.RegisterRequest;
import com.wallet.identity.domain.KycStatus;
import com.wallet.identity.repository.RefreshTokenRepository;
import com.wallet.identity.repository.UserRepository;
import com.wallet.shared.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
    outboxEventRepository.deleteAllInBatch();
  }

  @Test
  void testRegistrationAndLogin() throws Exception {
    RegisterRequest req = new RegisterRequest("test@wallet.com", "password123");

    // 1. Register
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    // 2. Login
    LoginRequest loginReq = new LoginRequest("test@wallet.com", "password123");
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  void testTokenRefreshAndRotation() throws Exception {
    RegisterRequest req = new RegisterRequest("refresh@wallet.com", "password123");

    MvcResult registerResult =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = registerResult.getResponse().getContentAsString();
    String refreshToken = objectMapper.readTree(responseBody).get("refreshToken").asText();

    // 1. Refresh with valid token
    RefreshRequest refreshReq = new RefreshRequest(refreshToken);
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(refreshReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    String newRefreshToken =
        objectMapper
            .readTree(refreshResult.getResponse().getContentAsString())
            .get("refreshToken")
            .asText();

    assertThat(newRefreshToken).isNotEqualTo(refreshToken);

    // 2. Attempt to use the OLD (now revoked) refresh token -> should detect theft and fail
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
        .andExpect(status().isBadRequest());

    // 3. Attempt to use the NEW refresh token -> should also fail because the family was revoked!
    RefreshRequest newRefreshReq = new RefreshRequest(newRefreshToken);
    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newRefreshReq)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testProtectedEndpointRequiresValidJwt() throws Exception {
    // 1. Access without token -> 401
    mockMvc.perform(get("/api/kyc/status")).andExpect(status().isUnauthorized());

    // 2. Register to get token
    RegisterRequest req = new RegisterRequest("secure@wallet.com", "password123");
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken =
        objectMapper
            .readTree(registerResult.getResponse().getContentAsString())
            .get("accessToken")
            .asText();

    // 3. Access with token -> 404 (endpoint doesn't exist, but it passed security)
    mockMvc
        .perform(get("/api/kyc/status").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void testKycSubmitAutoVerifiesAndPublishesOutboxEvent() throws Exception {
    RegisterRequest req = new RegisterRequest("kyc@wallet.com", "password123");
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken =
        objectMapper
            .readTree(registerResult.getResponse().getContentAsString())
            .get("accessToken")
            .asText();

    mockMvc
        .perform(post("/kyc/submit").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED"));

    assertThat(userRepository.findByEmail("kyc@wallet.com").orElseThrow().getKycStatus())
        .isEqualTo(KycStatus.VERIFIED);
    assertThat(outboxEventRepository.findAll())
        .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("user.kyc_verified"));
  }
}
