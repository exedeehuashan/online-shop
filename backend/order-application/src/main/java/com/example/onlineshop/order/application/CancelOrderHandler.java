package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.time.Clock;
import java.util.*;

public final class CancelOrderHandler implements CancelOrderUseCase {
  private final OrderRepository orders; private final DomainEventRepository events; private final Clock clock; private final TransactionRunner transactions;
  public CancelOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock) {
    this(orders, events, clock, TransactionRunner.direct());
  }
  public CancelOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner transactions) {
    this.orders = Objects.requireNonNull(orders); this.events = Objects.requireNonNull(events); this.clock = Objects.requireNonNull(clock); this.transactions = Objects.requireNonNull(transactions);
  }
  @Override public Order handle(CancelOrderCommand command) {
    return transactions.execute(() -> cancel(command));
  }
  private Order cancel(CancelOrderCommand command) {
    Objects.requireNonNull(command, "command");
    Order order = orders.findByIdAndCustomer(Objects.requireNonNull(command.orderId()), new CustomerReference(command.customerReference()))
        .orElseThrow(OrderNotFoundException::new);
    order.clearDomainEvents(); order.cancel(command.reason(), clock.instant());
    if (order.domainEvents().isEmpty()) return order;
    Order saved = orders.save(order); events.appendAll(order.domainEvents()); return saved;
  }
}
