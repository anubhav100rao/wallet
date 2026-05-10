package com.wallet.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxEventRepository repository;

  public OutboxPoller(OutboxEventRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedDelayString = "${wallet.outbox.poll-delay:PT30S}")
  @Transactional
  public void poll() {
    for (OutboxEvent event : repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
      log.info(
          "Outbox event ready: id={} type={} aggregateId={}",
          event.getId(),
          event.getEventType(),
          event.getAggregateId());
      event.markPublished();
    }
  }
}
