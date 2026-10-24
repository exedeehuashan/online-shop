package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
class OrderApiConfiguration {
  @Bean Clock clock() { return Clock.systemUTC(); }
  @Bean InternalIdGenerator internalIds(Clock clock) { return new TimeOrderedIdGenerator(0, clock); }
  @Bean PublicIdGenerator publicIds() { return UUID::randomUUID; }
  @Bean EventMetadataGenerator eventMetadata() { return EventMetadataGenerator.random(); }
  @Bean PostgresOrderRepository orders(JdbcTemplate jdbc) { return new PostgresOrderRepository(jdbc); }
  @Bean PostgresIdempotencyRepository idempotency(JdbcTemplate jdbc) { return new PostgresIdempotencyRepository(jdbc); }
  @Bean TransactionRunner transactions(DataSource source) { return new SpringTransactionRunner(new DataSourceTransactionManager(source)); }
  @Bean DomainEventRepository events(JdbcTemplate jdbc, InternalIdGenerator ids, ObjectMapper json) {
    return new PostgresOutboxRepository(jdbc, ids, json);
  }
  @Bean CreateOrderHandler create(PostgresOrderRepository orders, DomainEventRepository events, InternalIdGenerator ids,
      PublicIdGenerator publicIds, Clock clock, TransactionRunner tx, PostgresIdempotencyRepository idempotency,
      EventMetadataGenerator eventMetadata) {
    return new CreateOrderHandler(orders, events, ids, publicIds, clock, tx, idempotency, eventMetadata);
  }
  @Bean GetOrderUseCase get(PostgresOrderRepository orders) { return new GetOrderHandler(orders); }
  @Bean ListCustomerOrdersUseCase list(PostgresOrderRepository orders) { return new ListCustomerOrdersHandler(orders); }
  @Bean ConfirmOrderHandler confirm(PostgresOrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner tx, EventMetadataGenerator eventMetadata) {
    return new ConfirmOrderHandler(orders, events, clock, tx, eventMetadata);
  }
  @Bean CancelOrderHandler cancel(PostgresOrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner tx, EventMetadataGenerator eventMetadata) {
    return new CancelOrderHandler(orders, events, clock, tx, eventMetadata);
  }
}
