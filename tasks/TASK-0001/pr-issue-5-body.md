## Summary / 摘要

Implements PostgreSQL persistence for the Order bounded context with Flyway-owned schema, JDBC repository adapters, optimistic locking, idempotency records, Spring transactional boundaries, and a Transactional Outbox. / 为 Order 限界上下文实现 PostgreSQL 持久化，包括 Flyway 独占 schema、JDBC 仓储适配器、乐观锁、幂等记录、Spring 事务边界与 Transactional Outbox。

## Delivery metadata / 交付元数据

- **Priority / 优先级:** P0
- **Sequence / 顺序:** 3
- **Dependencies / 依赖:** #2, #4 (completed / 已完成)
- **Status / 状态:** IN REVIEW — QA REMEDIATED / 审核中—QA 缺陷已修复
- **Scope / 范围:** Issue #5 only; Issues #6–#11 are not started. / 仅 Issue #5；未开始 Issues #6–#11。

## Changes / 变更

- Flyway V1 creates `orders`, `order_items`, `order_idempotency`, and `outbox_events` with constraints and dual indexes, without PostgreSQL extensions. / Flyway V1 创建四张订单自有表、约束及双索引，不使用 PostgreSQL 扩展。
- Positive time-ordered BIGINT internal IDs coexist with UUID/opaque public references. / 正数时间有序 BIGINT 内部 ID 与 UUID/opaque 对外引用并存。
- Customer, product, address, and unit-price snapshots are reconstructed without emitting new domain events and are never updated during order mutation. / 客户、商品、地址与成交单价快照重建时不产生新领域事件，订单变更时不更新快照。
- Optimistic version updates fail with `OptimisticOrderLockException`; customer paging is stable by `created_at DESC, internal_id DESC`. / 乐观版本冲突抛出明确异常；客户订单按创建时间和内部 ID 稳定倒序分页。
- `SpringTransactionRunner` composes Order save and Outbox append in one PostgreSQL transaction. / `SpringTransactionRunner` 将订单保存与 Outbox 写入组合在同一个 PostgreSQL 事务中。
- Hibernate is configured as validation-only (`ddl-auto=validate`, no generated DDL); Flyway owns schema evolution. / Hibernate 仅校验 schema，不生成 DDL；Flyway 独占 schema 演进。
- Idempotency uses `ON CONFLICT DO NOTHING`, remains usable in the surrounding transaction, and returns the original public order UUID on replay. / 幂等写入使用 `ON CONFLICT DO NOTHING`，不会破坏外围事务，并在重放时返回原订单 public UUID。
- Database triggers enforce immutable order/item snapshots; explicit Hibernate mappings are validated against the Flyway schema. / 数据库触发器强制订单/订单项快照不可变；显式 Hibernate mappings 对 Flyway schema 执行校验。

## TDD and verification / TDD 与验证

- Red-test commit / 红灯测试提交: `d33983d`
- Implementation commit / 实现提交: `93b7883`
- QA red-test commit / QA 红灯测试提交: `3f55c29`
- Command / 命令: `DOCKER_HOST=unix:///home/hsl/.docker/desktop/docker.sock TESTCONTAINERS_RYUK_DISABLED=true mvn -B clean verify -Dapi.version=1.56`
- Result / 结果: **BUILD SUCCESS** — 45 total, 45 passed, 0 skipped, 0 failures, 0 errors. / 共 45 项，45 通过，0 跳过，0 失败，0 错误。
- Verified / 验证日期: 2026-10-23 against PostgreSQL 16.15 on Docker Engine 29.8.2. / 在 Docker Engine 29.8.2 的 PostgreSQL 16.15 上验证。
- The ten Testcontainers PostgreSQL tests additionally cover executable ORM validation, database-enforced snapshot immutability, in-transaction idempotent replay/conflict, and concurrent claims. / 10 项 Testcontainers PostgreSQL 测试新增覆盖可执行 ORM 校验、数据库强制快照不可变、事务内幂等重放/冲突及并发 claim。

## PostgreSQL gate / PostgreSQL 门禁

Docker Desktop 29 local command / 本机命令：`DOCKER_HOST=unix:///home/hsl/.docker/desktop/docker.sock TESTCONTAINERS_RYUK_DISABLED=true mvn -B clean verify -Dapi.version=1.56`. The ten PostgreSQL tests execute (no silent skip), and `.github/workflows/issue-5-postgres.yml` provides the Docker-enabled CI gate. / 10 项 PostgreSQL 测试必须执行（不允许静默跳过），并由 GitHub Actions 提供 CI 门禁。

Closes #5

Do not merge automatically; human review is required. / 禁止自动合并；需要人工审核。
