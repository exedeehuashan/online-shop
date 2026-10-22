# Add PostgreSQL persistence and Flyway migrations

实现批准后的订单 schema、repository adapter、幂等记录和乐观锁。

## Acceptance criteria

- Flyway 可从空 PostgreSQL 数据库完成迁移。
- ORM 仅验证 schema，不自动改表。
- Testcontainers 覆盖 repository、约束、唯一键、分页和乐观锁。
- 订单状态变更与 Outbox 写入处于同一事务。

## Dependencies

Blocked by design approval and order domain/application layer.
