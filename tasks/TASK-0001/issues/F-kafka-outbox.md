# Persist order events transactionally / 以事务方式持久化订单事件

Persist versioned order event envelopes through the Transactional Outbox. / 通过 Transactional Outbox 持久化版本化订单事件 envelope。

## Acceptance criteria / 验收标准

- Persist `OrderCreated.v1`, `OrderConfirmed.v1`, and `OrderCancelled.v1` envelopes in the same transaction as order mutation. / 与订单变更同一事务持久化三种版本化事件 envelope。
- PostgreSQL Testcontainers verifies atomic rollback, event ID uniqueness, aggregate ordering metadata, and immutable snapshots. / PostgreSQL Testcontainers 验证原子回滚、事件 ID 唯一性、聚合顺序元数据和不可变快照。
- Kafka publisher, retries, broker tests, delivery semantics, and consumer idempotency are deferred. / Kafka publisher、重试、broker 测试、投递语义与消费幂等均延后。

## Dependencies / 依赖

Blocked by design approval and persistence. / 受设计批准与持久化任务阻塞。
