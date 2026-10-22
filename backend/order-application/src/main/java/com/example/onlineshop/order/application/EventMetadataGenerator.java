package com.example.onlineshop.order.application;

import java.util.UUID;

@FunctionalInterface
public interface EventMetadataGenerator {
  EventMetadata next();

  record EventMetadata(UUID eventId, UUID correlationId) {
    public EventMetadata {
      if (eventId == null || correlationId == null) throw new IllegalArgumentException("event metadata IDs are required");
    }
  }

  static EventMetadataGenerator random() {
    return () -> new EventMetadata(UUID.randomUUID(), UUID.randomUUID());
  }
}
