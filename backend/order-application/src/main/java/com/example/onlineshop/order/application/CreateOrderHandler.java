package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.time.Clock;
import java.util.*;

public final class CreateOrderHandler implements CreateOrderUseCase {
  private final OrderRepository orders;
  private final DomainEventRepository events;
  private final InternalIdGenerator internalIds;
  private final PublicIdGenerator publicIds;
  private final Clock clock;
  private final TransactionRunner transactions;
  private final IdempotencyRepository idempotency;
  private final EventMetadataGenerator eventMetadata;

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock) {
    this(orders, events, internalIds, publicIds, clock, TransactionRunner.direct(), null, EventMetadataGenerator.random());
  }

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock, TransactionRunner transactions) {
    this(orders, events, internalIds, publicIds, clock, transactions, null, EventMetadataGenerator.random());
  }

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock, TransactionRunner transactions,
                            IdempotencyRepository idempotency) {
    this(orders, events, internalIds, publicIds, clock, transactions, idempotency, EventMetadataGenerator.random());
  }

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock, TransactionRunner transactions,
                            IdempotencyRepository idempotency, EventMetadataGenerator eventMetadata) {
    this.orders = Objects.requireNonNull(orders); this.events = Objects.requireNonNull(events);
    this.internalIds = Objects.requireNonNull(internalIds); this.publicIds = Objects.requireNonNull(publicIds);
    this.clock = Objects.requireNonNull(clock);
    this.transactions = Objects.requireNonNull(transactions);
    this.idempotency = idempotency;
    this.eventMetadata = Objects.requireNonNull(eventMetadata);
  }

  @Override public Order handle(CreateOrderCommand command) {
    Order order = transactions.execute(() -> persist(command));
    order.clearDomainEvents();
    return order;
  }

  public CreateOrderResult handle(CreateOrderCommand command, String key, String requestHash) {
    if (idempotency == null) throw new IllegalStateException("idempotency repository is required");
    try {
      CreateOrderResult result = transactions.execute(() -> {
        Order candidate = create(command);
        orders.save(candidate);
        IdempotencyClaim claim = idempotency.claim(command.customerReference(), key, requestHash,
            candidate.internalId(), candidate.id(), candidate.createdAt());
        if (claim.disposition() == IdempotencyClaim.Disposition.REPLAY) throw new ReplayDetected(claim.orderId());
        events.appendAll(candidate.domainEvents());
        return new CreateOrderResult(candidate, false);
      });
      result.order().clearDomainEvents();
      return result;
    } catch (ReplayDetected replay) {
      Order original = orders.findByIdAndCustomer(replay.orderId, new CustomerReference(command.customerReference()))
          .orElseThrow(OrderNotFoundException::new);
      return new CreateOrderResult(original, true);
    }
  }

  private static final class ReplayDetected extends RuntimeException {
    private final OrderId orderId;
    private ReplayDetected(OrderId orderId) { this.orderId = orderId; }
  }

  private Order persist(CreateOrderCommand command) {
    Order order = create(command); Order saved = orders.save(order); events.appendAll(order.domainEvents()); return saved;
  }
  private Order create(CreateOrderCommand command) {
    Objects.requireNonNull(command, "command");
    CreateOrderCommand.Address address = required(command.shippingAddress(), "shipping address");
    List<CreateOrderCommand.Item> requestedItems = required(command.items(), "items");
    OrderInternalId orderInternalId = new OrderInternalId(internalIds.nextId());
    List<OrderItem> items = requestedItems.stream().map(rawItem -> {
      CreateOrderCommand.Item item = required(rawItem, "order item");
      return new OrderItem(
        new OrderItemInternalId(internalIds.nextId()), new ProductReference(item.productReference()), null,
        item.sku(), item.productName(), Money.nzd(item.unitPrice()), item.quantity());
    }).toList();
    EventMetadataGenerator.EventMetadata metadata = eventMetadata.next();
    Order order = Order.create(orderInternalId, new OrderId(publicIds.nextId()),
        new CustomerReference(command.customerReference()), command.customerName(), items,
        new AddressSnapshot(address.recipient(), address.line1(), address.line2(), address.city(), address.region(),
            address.postalCode(), address.countryCode(), address.phone()), clock.instant(), metadata.eventId(), metadata.correlationId());
    return order;
  }

  private static <T> T required(T value, String field) {
    if (value == null) throw new DomainValidationException(DomainErrorCode.INVALID_VALUE, field + " is required");
    return value;
  }
}
