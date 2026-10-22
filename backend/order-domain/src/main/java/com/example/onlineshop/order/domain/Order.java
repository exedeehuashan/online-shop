package com.example.onlineshop.order.domain;

import java.time.Instant;
import java.util.*;

public final class Order {
  private final OrderInternalId internalId;
  private final OrderId id;
  private final CustomerReference customerReference;
  private final Long customerInternalId;
  private final String customerNameSnapshot;
  private final List<OrderItem> items;
  private final AddressSnapshot shippingAddress;
  private final Money total;
  private final Instant createdAt;
  private final List<DomainEvent> domainEvents = new ArrayList<>();
  private OrderStatus status;
  private String cancellationReason;
  private Instant updatedAt;
  private long version;
  private long persistedVersion = -1;

  private Order(OrderInternalId internalId, OrderId id, CustomerReference customerReference, Long customerInternalId,
                String customerNameSnapshot, List<OrderItem> items, AddressSnapshot shippingAddress, Instant now) {
    this.internalId = Objects.requireNonNull(internalId);
    this.id = Objects.requireNonNull(id);
    this.customerReference = Objects.requireNonNull(customerReference);
    if (customerInternalId != null && customerInternalId <= 0) throw Validation.invalid("customer internal ID must be positive");
    this.customerInternalId = customerInternalId;
    this.customerNameSnapshot = Validation.required(customerNameSnapshot, 200, "customer name snapshot");
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) throw new DomainValidationException(DomainErrorCode.EMPTY_ORDER, "order must contain at least one item");
    HashSet<String> skus = new HashSet<>();
    for (OrderItem item : items) {
      if (item == null) throw Validation.invalid("order item is required");
      if (!skus.add(item.sku())) throw new DomainValidationException(DomainErrorCode.DUPLICATE_SKU, "duplicate SKU: " + item.sku());
    }
    this.items = List.copyOf(items);
    this.shippingAddress = Objects.requireNonNull(shippingAddress);
    this.total = items.stream().map(OrderItem::lineTotal).reduce(Money.zeroNzd(), Money::add);
    this.status = OrderStatus.DRAFT;
    this.createdAt = Objects.requireNonNull(now);
    this.updatedAt = now;
    this.domainEvents.add(new OrderCreated(id, internalId, now));
  }

  public static Order create(OrderInternalId internalId, OrderId id, CustomerReference customerReference,
                             String customerNameSnapshot, List<OrderItem> items, AddressSnapshot address, Instant now) {
    return new Order(internalId, id, customerReference, null, customerNameSnapshot, items, address, now);
  }

  public static Order reconstitute(OrderInternalId internalId, OrderId id, CustomerReference customerReference,
                                   Long customerInternalId, String customerNameSnapshot, List<OrderItem> items,
                                   AddressSnapshot address, OrderStatus status, String cancellationReason,
                                   Instant createdAt, Instant updatedAt, long version) {
    Order order = new Order(internalId, id, customerReference, customerInternalId, customerNameSnapshot, items, address, createdAt);
    order.status = Objects.requireNonNull(status);
    order.cancellationReason = cancellationReason;
    order.updatedAt = Objects.requireNonNull(updatedAt);
    if (version < 0) throw Validation.invalid("version must be non-negative");
    order.version = version;
    order.persistedVersion = version;
    order.domainEvents.clear();
    return order;
  }

  public void confirm(Instant now) {
    Objects.requireNonNull(now);
    if (status == OrderStatus.CONFIRMED) return;
    if (status != OrderStatus.DRAFT) throw new OrderStateConflictException("cannot confirm an order in " + status + " state");
    status = OrderStatus.CONFIRMED; updatedAt = now; version++;
    domainEvents.add(new OrderConfirmed(id, internalId, now));
  }

  public void cancel(String reason, Instant now) {
    Objects.requireNonNull(now);
    if (status == OrderStatus.CANCELLED) return;
    String validatedReason = Validation.required(reason, 500, "cancellation reason");
    status = OrderStatus.CANCELLED; cancellationReason = validatedReason; updatedAt = now; version++;
    domainEvents.add(new OrderCancelled(id, internalId, now, validatedReason));
  }

  public OrderInternalId internalId() { return internalId; }
  public OrderId id() { return id; }
  public CustomerReference customerReference() { return customerReference; }
  public Long customerInternalId() { return customerInternalId; }
  public String customerNameSnapshot() { return customerNameSnapshot; }
  public List<OrderItem> items() { return items; }
  public AddressSnapshot shippingAddress() { return shippingAddress; }
  public Money total() { return total; }
  public OrderStatus status() { return status; }
  public Optional<String> cancellationReason() { return Optional.ofNullable(cancellationReason); }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }
  public long version() { return version; }
  public long persistedVersion() { return persistedVersion; }
  public void markPersisted() { persistedVersion = version; }
  public List<DomainEvent> domainEvents() { return List.copyOf(domainEvents); }
  public void clearDomainEvents() { domainEvents.clear(); }
}
