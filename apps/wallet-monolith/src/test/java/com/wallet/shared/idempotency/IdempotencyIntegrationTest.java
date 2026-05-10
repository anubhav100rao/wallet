package com.wallet.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@ActiveProfiles("test") // Uses Testcontainers DB
class IdempotencyIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DummyIdempotentController dummyController;
  @Autowired private IdempotencyKeyRepository repository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    dummyController.reset();
    repository.deleteAllInBatch();
  }

  @Test
  void testIdempotency_SamePayload_ReturnsReplay() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    Map<String, Object> payload = Map.of("amount", 100, "currency", "USD");
    String jsonPayload = objectMapper.writeValueAsString(payload);

    // First request
    mockMvc
        .perform(
            post("/api/dummy/mutate")
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executionCount").value(1));

    assertThat(dummyController.getExecutionCount()).isEqualTo(1);

    // Second request with same key and payload
    mockMvc
        .perform(
            post("/api/dummy/mutate")
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executionCount").value(1)); // Should replay the first response

    // Business logic should NOT have executed again
    assertThat(dummyController.getExecutionCount()).isEqualTo(1);

    // Database should contain exactly 1 key
    assertThat(repository.count()).isEqualTo(1);
    IdempotencyKeyEntity key = repository.findById(idempotencyKey).orElseThrow();
    assertThat(key.getResponseStatus()).isEqualTo(200);
  }

  @Test
  void testIdempotency_DifferentPayload_ReturnsConflict() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();

    String payload1 = objectMapper.writeValueAsString(Map.of("amount", 100));
    String payload2 = objectMapper.writeValueAsString(Map.of("amount", 200)); // Different!

    // First request
    mockMvc
        .perform(
            post("/api/dummy/mutate")
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload1))
        .andExpect(status().isOk());

    // Second request with SAME key but DIFFERENT payload
    mockMvc
        .perform(
            post("/api/dummy/mutate")
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload2))
        .andExpect(status().isConflict()); // 409 Conflict

    assertThat(dummyController.getExecutionCount()).isEqualTo(1);
  }

  @Test
  void testIdempotency_MissingKey_ReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/api/dummy/mutate").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest()); // 400 Bad Request
  }

  @Test
  void testIdempotency_ConcurrentRequests_OnlyOneExecutes() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    String jsonPayload = objectMapper.writeValueAsString(Map.of("concurrent", true));

    int numThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    List<Callable<MvcResult>> tasks = new ArrayList<>();

    for (int i = 0; i < numThreads; i++) {
      tasks.add(
          () ->
              mockMvc
                  .perform(
                      post("/api/dummy/mutate")
                          .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(jsonPayload))
                  .andReturn());
    }

    List<Future<MvcResult>> futures = executor.invokeAll(tasks);

    int okCount = 0;
    int conflictCount = 0;

    for (Future<MvcResult> future : futures) {
      MvcResult result = future.get();
      int status = result.getResponse().getStatus();
      if (status == 200) {
        okCount++;
      } else if (status == 409) {
        // We might get 409 if a concurrent request finds the key but the first request
        // hasn't finished writing the response yet.
        conflictCount++;
      }
    }

    // Only ONE request should have executed the business logic
    assertThat(dummyController.getExecutionCount()).isEqualTo(1);

    // At least the first one should be 200 OK. The rest might be 200 (if they hit after
    // the first one finished) or 409 (if they hit while the first one was processing).
    assertThat(okCount).isGreaterThanOrEqualTo(1);

    // Database should contain exactly 1 key
    assertThat(repository.count()).isEqualTo(1);
  }
}
