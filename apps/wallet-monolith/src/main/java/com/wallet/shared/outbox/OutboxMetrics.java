package com.wallet.shared.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Exposes a gauge tracking the count of outbox events still waiting to be published. Intended for
 * the Grafana dashboard's lag/backlog indicator and for an alerting rule on a sustained backlog.
 */
@Component
public class OutboxMetrics {

  public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
    Gauge.builder(
            "banking.outbox.unpublished",
            repository,
            OutboxEventRepository::countByPublishedAtIsNull)
        .description("Number of outbox rows with published_at IS NULL")
        .register(registry);
  }
}
