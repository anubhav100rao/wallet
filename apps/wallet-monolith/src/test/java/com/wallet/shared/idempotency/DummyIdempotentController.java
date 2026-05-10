package com.wallet.shared.idempotency;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DummyIdempotentController {

  private final AtomicInteger executionCount = new AtomicInteger(0);

  @Idempotent
  @PostMapping("/api/dummy/mutate")
  public Map<String, Object> mutate(@RequestBody Map<String, Object> payload) {
    int count = executionCount.incrementAndGet();
    return Map.of(
        "success", true,
        "executionCount", count,
        "receivedPayload", payload);
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  public void reset() {
    executionCount.set(0);
  }
}
