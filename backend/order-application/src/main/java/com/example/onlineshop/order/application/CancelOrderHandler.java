package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.time.Clock;
import java.util.*;

public final class CancelOrderHandler implements CancelOrderUseCase {
  private final OrderRepository orders; private final DomainEventRepository events; private final Clock clock; private final TransactionRunner transactions; private final EventMetadataGenerator eventMetadata;
  public CancelOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock) {
    this(orders, events, clock, TransactionRunner.direct(), EventMetadataGenerator.random());
  }
  public CancelOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner transactions) {
    this(orders, events, clock, transactions, EventMetadataGenerator.random());
  }
  public CancelOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner transactions, EventMetadataGenerator eventMetadata) {
    this.orders = Objects.requireNonNull(orders); this.events = Objects.requireNonNull(events); this.clock = Objects.requireNonNull(clock); this.transactions = Objects.requireNonNull(transactions); this.eventMetadata = Objects.requireNonNull(eventMetadata);
  }
  @Override public Order handle(CancelOrderCommand command) {
    Order order = transactions.execute(() -> cancel(command));
    order.clearDomainEvents();
    return order;
  }
  private Order cancel(CancelOrderCommand command) {
    Objects.requireNonNull(command, "command");
    Order order = orders.findByIdAndCustomer(Objects.requireNonNull(command.orderId()), new CustomerReference(command.customerReference()))
        .orElseThrow(OrderNotFoundException::new);
    if (command.expectedVersion() != null && command.expectedVersion() != order.version()) throw new OptimisticOrderLockException();
    order.clearDomainEvents(); EventMetadataGenerator.EventMetadata metadata = eventMetadata.next(); order.cancel(command.reason(), clock.instant(), metadata.eventId(), metadata.correlationId());
    if (order.domainEvents().isEmpty()) return order;
    Order saved = orders.save(order); events.appendAll(order.domainEvents()); return saved;
  }
}
