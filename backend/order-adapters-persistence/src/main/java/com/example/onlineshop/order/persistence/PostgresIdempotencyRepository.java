package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.OrderId;
import com.example.onlineshop.order.domain.OrderInternalId;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresIdempotencyRepository {
  private final JdbcTemplate jdbc;
  public PostgresIdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public IdempotencyClaim claim(String customer, String key, String hash, OrderInternalId internalId,
                                OrderId publicId, Instant at) {
    int inserted = jdbc.update("""
        insert into order_idempotency(customer_ref,idempotency_key,request_hash,order_internal_id,order_public_id,created_at)
        values (?,?,?,?,?,?) on conflict (customer_ref,idempotency_key) do nothing""",
        customer, key, hash, internalId.value(), publicId.value(), OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    if (inserted == 1) return IdempotencyClaim.created(publicId);

    ExistingClaim existing = jdbc.queryForObject("""
        select request_hash,order_public_id from order_idempotency
        where customer_ref=? and idempotency_key=?""",
        (rs, row) -> new ExistingClaim(rs.getString(1), rs.getObject(2, UUID.class)), customer, key);
    if (!hash.equals(existing.requestHash())) throw new IdempotencyConflictException();
    return IdempotencyClaim.replay(new OrderId(existing.orderPublicId()));
  }

  private record ExistingClaim(String requestHash, UUID orderPublicId) {}
}
