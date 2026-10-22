package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.OrderInternalId;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresIdempotencyRepository {
  private final JdbcTemplate jdbc;
  public PostgresIdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public IdempotencyClaim claim(String customer, String key, String hash, OrderInternalId orderId, Instant at) {
    try {
      jdbc.update("insert into order_idempotency(customer_ref,idempotency_key,request_hash,order_internal_id,created_at) values (?,?,?,?,?)",
          customer, key, hash, orderId.value(), at);
      return IdempotencyClaim.CREATED;
    } catch (DuplicateKeyException duplicate) {
      String existing = jdbc.queryForObject("select request_hash from order_idempotency where customer_ref=? and idempotency_key=?", String.class, customer, key);
      if (hash.equals(existing)) return IdempotencyClaim.REPLAY;
      throw new IdempotencyConflictException();
    }
  }
}
