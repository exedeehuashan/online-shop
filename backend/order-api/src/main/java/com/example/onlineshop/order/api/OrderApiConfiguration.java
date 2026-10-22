package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.persistence.*;
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
  @Bean PostgresOrderRepository orders(JdbcTemplate jdbc) { return new PostgresOrderRepository(jdbc); }
  @Bean PostgresIdempotencyRepository idempotency(JdbcTemplate jdbc) { return new PostgresIdempotencyRepository(jdbc); }
  @Bean TransactionRunner transactions(DataSource source) { return new SpringTransactionRunner(new DataSourceTransactionManager(source)); }
  @Bean DomainEventRepository events(JdbcTemplate jdbc, InternalIdGenerator ids, PublicIdGenerator publicIds) {
    return new PostgresOutboxRepository(jdbc, ids, publicIds);
  }
  @Bean CreateOrderHandler create(PostgresOrderRepository orders, DomainEventRepository events, InternalIdGenerator ids,
      PublicIdGenerator publicIds, Clock clock, TransactionRunner tx, PostgresIdempotencyRepository idempotency) {
    return new CreateOrderHandler(orders, events, ids, publicIds, clock, tx, idempotency);
  }
  @Bean GetOrderUseCase get(PostgresOrderRepository orders) { return new GetOrderHandler(orders); }
  @Bean ListCustomerOrdersUseCase list(PostgresOrderRepository orders) { return new ListCustomerOrdersHandler(orders); }
  @Bean ConfirmOrderUseCase confirm(PostgresOrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner tx) {
    return new ConfirmOrderHandler(orders, events, clock, tx);
  }
  @Bean CancelOrderUseCase cancel(PostgresOrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner tx) {
    return new CancelOrderHandler(orders, events, clock, tx);
  }
}
