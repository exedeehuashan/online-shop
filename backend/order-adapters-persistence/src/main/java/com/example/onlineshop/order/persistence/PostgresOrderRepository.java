package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresOrderRepository implements OrderRepository, OrderAuditRepository {
  private static final String SELECT_ORDER = """
      select internal_id,public_id,customer_ref,customer_internal_id,customer_name_snapshot,status,currency,total_amount,
             recipient,address_line1,address_line2,city,region,postal_code,country_code,phone,cancellation_reason,version,created_at,updated_at
      from orders""";
  private final JdbcTemplate jdbc;
  public PostgresOrderRepository(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

  @Override public Order save(Order order) {
    return save(order, null);
  }

  @Override public Order save(Order order, UUID createdBy) {
    Integer count = jdbc.queryForObject("select count(*) from orders where internal_id=?", Integer.class, order.internalId().value());
    if (count == 0) insert(order, createdBy); else update(order);
    order.markPersisted();
    return order;
  }

  private void insert(Order order, UUID createdBy) {
    AddressSnapshot a = order.shippingAddress();
    jdbc.update("""
        insert into orders(internal_id,public_id,customer_ref,customer_internal_id,customer_name_snapshot,status,currency,total_amount,item_count,
          recipient,address_line1,address_line2,city,region,postal_code,country_code,phone,cancellation_reason,version,created_at,updated_at,created_by,updated_by)
        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        order.internalId().value(), order.id().value(), order.customerReference().value(), order.customerInternalId(), order.customerNameSnapshot(),
        order.status().name(), order.total().currency(), order.total().amount(), order.items().size(), a.recipient(), a.line1(), a.line2(), a.city(), a.region(),
        a.postalCode(), a.countryCode(), a.phone(), order.cancellationReason().orElse(null), order.version(), utc(order.createdAt()), utc(order.updatedAt()), createdBy, createdBy);
    for (OrderItem item : order.items()) {
      jdbc.update("""
          insert into order_items(internal_id,order_internal_id,product_ref,product_internal_id,sku,product_name_snapshot,
            unit_amount_snapshot,currency,quantity,line_total) values (?,?,?,?,?,?,?,?,?,?)""",
          item.internalId().value(), order.internalId().value(), item.productReference().value(), item.productInternalId(), item.sku(),
          item.productNameSnapshot(), item.unitPriceSnapshot().amount(), item.unitPriceSnapshot().currency(), item.quantity(), item.lineTotal().amount());
    }
  }

  private void update(Order order) {
    long expectedVersion = order.persistedVersion();
    int changed = jdbc.update("update orders set status=?,cancellation_reason=?,version=?,updated_at=? where internal_id=? and version=?",
        order.status().name(), order.cancellationReason().orElse(null), order.version(), utc(order.updatedAt()), order.internalId().value(), expectedVersion);
    if (changed != 1) throw new OptimisticOrderLockException();
  }

  @Override public Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customer) {
    List<OrderRow> rows = jdbc.query(SELECT_ORDER + " where public_id=? and customer_ref=?", this::mapOrderRow, id.value(), customer.value());
    return rows.stream().findFirst().map(this::reconstitute);
  }

  @Override public List<Order> findByCustomer(CustomerReference customer, int page, int size) {
    long offset = Math.multiplyExact((long) page, size);
    return jdbc.query(SELECT_ORDER + " where customer_ref=? order by created_at desc,internal_id desc limit ? offset ?", this::mapOrderRow,
        customer.value(), size, offset).stream().map(this::reconstitute).toList();
  }

  @Override public Optional<Order> findById(OrderId id) {
    return jdbc.query(SELECT_ORDER + " where public_id=?", this::mapOrderRow, id.value()).stream().findFirst().map(this::reconstitute);
  }

  @Override public List<Order> findAll(int page, int size) {
    long offset = Math.multiplyExact((long) page, size);
    return jdbc.query(SELECT_ORDER + " order by created_at desc,internal_id desc limit ? offset ?", this::mapOrderRow,
        size, offset).stream().map(this::reconstitute).toList();
  }

  @Override public void setCreatedActor(OrderInternalId id, UUID actorId) {
    throw new UnsupportedOperationException("created actor must be supplied at insert");
  }

  @Override public void setUpdatedActor(OrderInternalId id, UUID actorId) {
    jdbc.update("update orders set updated_by=? where internal_id=?", actorId, id.value());
  }

  @Override public void append(Order order, UUID actorId, String action, Instant occurredAt, String correlationId) {
    jdbc.update("""
        insert into order_audit_events(event_id,order_internal_id,order_public_id,actor_id,action,occurred_at,correlation_id,outcome)
        values (?,?,?,?,?,?,?,'SUCCEEDED')""", UUID.randomUUID(), order.internalId().value(), order.id().value(), actorId,
        action, utc(occurredAt), Objects.requireNonNullElse(correlationId, UUID.randomUUID().toString()));
  }

  private Order reconstitute(OrderRow row) {
    List<OrderItem> items = jdbc.query("""
        select internal_id,product_ref,product_internal_id,sku,product_name_snapshot,unit_amount_snapshot,currency,quantity,line_total
        from order_items where order_internal_id=? order by internal_id""", (rs, n) -> new OrderItem(
        new OrderItemInternalId(rs.getLong("internal_id")), new ProductReference(rs.getString("product_ref")), nullableLong(rs, "product_internal_id"),
        rs.getString("sku"), rs.getString("product_name_snapshot"), new Money(rs.getBigDecimal("unit_amount_snapshot"), rs.getString("currency").trim()),
        rs.getInt("quantity"), new Money(rs.getBigDecimal("line_total"), rs.getString("currency").trim())), row.internalId);
    return Order.reconstitute(new OrderInternalId(row.internalId), new OrderId(row.publicId), new CustomerReference(row.customerRef), row.customerInternalId,
        row.customerName, items, row.address, row.status, row.cancellationReason, row.createdAt, row.updatedAt, row.version);
  }

  private OrderRow mapOrderRow(ResultSet rs, int rowNum) throws SQLException {
    return new OrderRow(rs.getLong("internal_id"), rs.getObject("public_id", UUID.class), rs.getString("customer_ref"), nullableLong(rs,"customer_internal_id"),
        rs.getString("customer_name_snapshot"), OrderStatus.valueOf(rs.getString("status")),
        new AddressSnapshot(rs.getString("recipient"),rs.getString("address_line1"),rs.getString("address_line2"),rs.getString("city"),rs.getString("region"),
            rs.getString("postal_code"),rs.getString("country_code").trim(),rs.getString("phone")),
        rs.getString("cancellation_reason"),rs.getLong("version"),rs.getObject("created_at",java.time.OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at",java.time.OffsetDateTime.class).toInstant());
  }
  private static Long nullableLong(ResultSet rs, String column) throws SQLException { long value=rs.getLong(column); return rs.wasNull()?null:value; }
  private static OffsetDateTime utc(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }
  private record OrderRow(long internalId, UUID publicId, String customerRef, Long customerInternalId, String customerName,
                          OrderStatus status, AddressSnapshot address, String cancellationReason, long version, Instant createdAt, Instant updatedAt) {}
}
