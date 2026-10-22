package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.time.Clock;
import java.util.*;

public final class ConfirmOrderHandler implements ConfirmOrderUseCase {
  private final OrderRepository orders; private final DomainEventRepository events; private final Clock clock; private final TransactionRunner transactions;
  public ConfirmOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock) {
    this(orders, events, clock, TransactionRunner.direct());
  }
  public ConfirmOrderHandler(OrderRepository orders, DomainEventRepository events, Clock clock, TransactionRunner transactions) {
    this.orders = Objects.requireNonNull(orders); this.events = Objects.requireNonNull(events); this.clock = Objects.requireNonNull(clock); this.transactions = Objects.requireNonNull(transactions);
  }
  @Override public Order handle(ConfirmOrderCommand command) {
    return transactions.execute(() -> confirm(command));
  }
  private Order confirm(ConfirmOrderCommand command) {
    Objects.requireNonNull(command, "command");
    Order order = orders.findByIdAndCustomer(Objects.requireNonNull(command.orderId()), new CustomerReference(command.customerReference()))
        .orElseThrow(OrderNotFoundException::new);
    order.clearDomainEvents(); order.confirm(clock.instant());
    if (order.domainEvents().isEmpty()) return order;
    Order saved = orders.save(order); events.appendAll(order.domainEvents()); return saved;
  }
}
