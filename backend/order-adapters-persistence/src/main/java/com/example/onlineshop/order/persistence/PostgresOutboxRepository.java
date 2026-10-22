package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresOutboxRepository implements DomainEventRepository {
  private final JdbcTemplate jdbc;
  private final InternalIdGenerator internalIds;
  private final PublicIdGenerator eventIds;
  private final ObjectMapper json = new ObjectMapper();
  public PostgresOutboxRepository(JdbcTemplate jdbc, InternalIdGenerator internalIds, PublicIdGenerator eventIds) {
    this.jdbc = Objects.requireNonNull(jdbc); this.internalIds = Objects.requireNonNull(internalIds); this.eventIds = Objects.requireNonNull(eventIds);
  }
  @Override public void appendAll(List<DomainEvent> events) {
    for (DomainEvent event : events) {
      long internalId = internalIds.nextId();
      jdbc.update("insert into outbox_events(internal_id,event_id,aggregate_type,aggregate_public_id,aggregate_internal_id,event_type,event_version,payload,occurred_at) values (?,?,?,?,?,?,?,?::jsonb,?)",
          internalId, eventIds.nextId(), "Order", event.orderId().value(), event.orderInternalId().value(), event.eventType(), event.eventVersion(), payload(event), event.occurredAt());
    }
  }
  private String payload(DomainEvent event) {
    Map<String,Object> payload = new LinkedHashMap<>();
    payload.put("orderId", event.orderId().value().toString());
    if (event instanceof OrderCancelled cancelled) payload.put("reason", cancelled.reason());
    try { return json.writeValueAsString(payload); }
    catch (JsonProcessingException impossible) { throw new IllegalStateException("cannot serialize outbox payload", impossible); }
  }
}
