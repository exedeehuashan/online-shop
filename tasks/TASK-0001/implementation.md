# TASK-0001：实现记录 / Implementation Record

## Design-stage note / 设计阶段说明

- 状态 / Status：`NOT_STARTED / PENDING_APPROVAL`。
- 本 PR 仅修订需求、架构、测试计划、路线图和 GitHub backlog；没有应用代码、迁移、测试脚本、容器或部署变更。/ This PR revises requirements, architecture, test planning, roadmap, and backlog only; it contains no application code, migrations, test scripts, containers, or deployment changes.
- 人类批准 #2 前不得进入 `DEVELOPING`。/ Do not enter `DEVELOPING` until a human approves #2.
- 批准后，由明确指定的 developer/tester agents 在人类监督下工作，并各自在 feature branch 通过 PR 交付；禁止直接提交 `main`、自动合并、部署或生产变更。/ After approval, explicitly assigned developer/tester agents work under human supervision using feature branches and PRs; no direct main commits, auto-merge, deployment, or production changes.

## Approval record / 批准记录

批准时必须记录：批准人/日期、API 与认证选择、数据库 schema/Flyway 决策、模块/事件边界、Outbox-only MVP/Kafka publisher 延后、Redis/Elasticsearch 延后策略，以及已确认设计决策。/ Approval must record approver/date, API and auth choices, database schema/Flyway decision, module/event boundaries, Outbox-only MVP/Kafka publisher deferral, Redis/Elasticsearch deferral, and the confirmed design decisions.

当前决策记录 / Current decision record（2026-10-22）：用户已确认客户端临时价格快照、public/opaque + internal time-ordered 双 ID、重放 `200`、语义错误 `422`、购物车合并而订单 API 拒绝重复 SKU、仅 Transactional Outbox、不可变冗余快照。/ The user confirmed temporary client price snapshots, public/opaque plus internal time-ordered IDs, replay `200`, semantic `422`, cart-side merge with order-API duplicate rejection, Transactional Outbox only, and immutable redundant snapshots.

整体实施批准 / Overall implementation approval：用户于 2026-10-22 批准 PR #18；Issue #4 已获明确授权，由 developer agent 在人工监督下实施。/ The user approved PR #18 on 2026-10-22; Issue #4 was explicitly authorized for implementation by a developer agent under human supervision.

## Issue #4 implementation / Issue #4 实施

- 状态 / Status：`DEVELOPING / 开发中`（PR #19 独立 QA 的 `FAILED` 后修复 D1–D3）。/ Remediation of D1–D3 after PR #19 independent QA moved the work from `FAILED` back to `DEVELOPING`.
- 范围 / Scope：建立 Java 17 Maven 多模块基线、纯 Java `Order` 聚合、Money/地址/订单项/引用值对象、状态机、领域事件、入站用例与出站端口、Clock/UUID/63-bit 时间有序 ID 注入，以及 ArchUnit 依赖方向规则。/ Established the Java 17 Maven multi-module baseline, pure-Java `Order` aggregate, Money/address/item/reference value objects, state machine, domain events, inbound use cases and outbound ports, injected Clock/UUID/63-bit time-ordered IDs, and ArchUnit dependency-direction rules.
- TDD 证据 / TDD evidence：先增加引用尚不存在生产类型的行为测试；首次 Maven 红灯在依赖引导阶段中止，随后以最小领域与应用实现转绿；开发者验证命令为 `mvn test`。/ Behavior tests referencing not-yet-existing production types were added first; the initial red Maven run stopped during dependency bootstrap, followed by the minimum domain/application implementation to reach green; developer verification command: `mvn test`.
- 边界 / Boundaries：未实现 PostgreSQL/Flyway、REST、Spring、JPA、Kafka publisher、Web UI、Docker 或后续 Issue #5–#11。事务端口已定义，真实事务适配器留给持久化增量。/ No PostgreSQL/Flyway, REST, Spring, JPA, Kafka publisher, web UI, Docker, or later Issue #5–#11 work was implemented. A transaction port is defined; its real adapter remains for the persistence increment.
- 交付 / Delivery：功能分支 `feat/issue-4-order-domain-application`，PR #19 等待人工审核，不自动合并。/ Feature branch `feat/issue-4-order-domain-application`; PR #19 awaits human review with no automatic merge.

### PR #19 QA remediation / PR #19 QA 修复

- D1：epoch/node 0 的首个 ID 显式跳过保留值 `0`；最大 41-bit 时间边界仍保持正 63-bit，且补充首两个 ID、序列耗尽、最大/越界时间测试。/ The first epoch/node-0 ID skips reserved value `0`; the maximum 41-bit time boundary remains a positive 63-bit value, with regression coverage for the first two IDs, sequence exhaustion, and maximum/out-of-range timestamps.
- D2：空订单项及空单价等语义非法输入统一转为 `DomainValidationException(INVALID_VALUE)`，为未来 REST `422` 映射保留稳定契约。/ Semantically invalid null order items and unit prices now consistently raise `DomainValidationException(INVALID_VALUE)`, preserving a stable contract for future REST `422` mapping.
- D3：opaque 客户/商品引用不再静默 trim；空白值及含首尾空格的值均显式拒绝，避免标识碰撞。/ Opaque customer/product references are no longer silently trimmed; blank values and surrounding whitespace are explicitly rejected to prevent identifier collisions.
- 范围仍严格限于 Issue #4；未实现或修改 Issues #5–#11，未合并 PR。/ Scope remains strictly limited to Issue #4; Issues #5–#11 were not implemented or changed, and the PR was not merged.

## Issue #5 implementation / Issue #5 实施

- 状态 / Status：`IN_REVIEW / 审核中`；优先级 / Priority：P0；顺序 / Sequence：3；依赖 / Dependencies：#2、#4（均已完成 / both completed）。
- 分支 / Branch：`feat/issue-5-postgres-flyway`；基线 / Baseline：`c13c654`。
- 范围 / Scope：Flyway V1 PostgreSQL schema、JDBC repository adapters、乐观锁、幂等记录、Spring transaction adapter、Transactional Outbox，以及 Testcontainers PostgreSQL tests。未实现 Kafka publisher、REST、前端或 Issues #6–#11。/ Flyway V1 PostgreSQL schema, JDBC repository adapters, optimistic locking, idempotency records, Spring transaction adapter, Transactional Outbox, and Testcontainers PostgreSQL tests. Kafka publishing, REST, frontend, and Issues #6–#11 remain untouched.
- 设计保持 / Preserved design：大表使用应用生成的正数 BIGINT 内部键，对外订单/事件使用 UUID，客户/商品使用 opaque refs，并建立时间线、public ID、内部关联与审计双索引；不使用 PostgreSQL extension。数据库触发器拒绝订单及订单项快照更新，正常 mutation 只允许状态、取消原因、版本和更新时间。/ Large tables use application-generated positive BIGINT internal keys, orders/events use public UUIDs, customer/product IDs remain opaque, and timeline/public/internal/audit indexes are present without PostgreSQL extensions. Database triggers reject order and item snapshot changes; normal mutations only change status, cancellation reason, version, and update time.
- TDD / TDD：提交 `d33983d` 先定义缺失 adapter 的 PostgreSQL acceptance tests；提交 `93b7883` 加入最小 schema/adapters 使代码可编译并进入容器验证阶段。/ Commit `d33983d` defined PostgreSQL acceptance tests against absent adapters first; `93b7883` supplied the minimum schema/adapters to compile and reach container verification.
- QA 修复 / QA remediation：`INSERT ... ON CONFLICT DO NOTHING` 避免唯一键冲突使事务进入 aborted 状态；幂等结果携带首次订单 public UUID；事务内 replay/conflict 与双线程竞争均有 PostgreSQL 回归测试。/ `INSERT ... ON CONFLICT DO NOTHING` prevents unique conflicts from aborting the transaction; idempotency results carry the first order's public UUID; PostgreSQL regressions cover in-transaction replay/conflict and a two-thread race.
- ORM/Flyway ownership：显式 Hibernate mappings 在 Testcontainers 启动时以 `validate` 对 Flyway schema 执行真实校验，绝不生成 DDL；Flyway 为唯一 schema owner。/ Explicit Hibernate mappings execute real `validate` against the Flyway schema during Testcontainers startup and never generate DDL; Flyway remains the sole schema owner.
- 修复 TDD / Remediation TDD：提交 `3f55c29` 记录 QA 缺陷的失败测试，后续实现提交修复。/ Commit `3f55c29` records failing tests for the QA findings before the remediation implementation.
- 真实数据库验证 / Real database verification：2026-10-23 在 Docker Engine 29.8.2 上执行 PostgreSQL 16.15；定向 Testcontainers 套件 10/10 通过，完整 `mvn clean verify` 45/45 通过，均为 0 skipped。/ On 2026-10-23 PostgreSQL 16.15 ran on Docker Engine 29.8.2; the focused Testcontainers suite passed 10/10 and the full clean verification passed 45/45, both with zero skipped tests.

## Issue #5 completion and Issue #6 start / Issue #5 完成与 Issue #6 启动

- 人工批准 / Human approval：用户于 2026-10-23 批准合并 PR #20 并启动 Issue #6。/ The user approved merging PR #20 and starting Issue #6 on 2026-10-23.
- 合并 / Merge：PR #20 已合并至 `main`，merge commit 为 `bacde8d0ea2f8158016dc34a45294d4b1787834d`。/ PR #20 was merged into `main` with merge commit `bacde8d0ea2f8158016dc34a45294d4b1787834d`.
- Issue #6 状态 / Issue #6 status：`DEVELOPING / 开发中`；由明确指定的 developer agent 在独立功能分支实施版本化订单 REST API 与 OpenAPI，通过 PR 交付，不自动合并。/ An explicitly assigned developer agent implements the versioned order REST API and OpenAPI on a feature branch for delivery by PR, with no automatic merge.

## Issue #6 implementation / Issue #6 实施

- 状态 / Status：`IN_REVIEW / 待审核`；分支 / Branch：`feat/issue-6-order-rest-api`；基线 / Baseline：`f9beff8`；交付 / Delivery：PR #21（禁止自动合并 / no auto-merge）。
- REST/OpenAPI：新增 Spring Boot `order-api` 组合根及 `/api/v1/customers/{customerId}/orders` 创建、列表、详情、确认、取消端点；Springdoc `/v3/api-docs` 记录成功、400、422、404、409 契约及 customer ID 仅限开发/受控 UAT。/ Added the Spring Boot `order-api` composition root and approved create/list/detail/confirm/cancel endpoints; Springdoc documents success, 400, 422, 404, and 409 contracts plus the dev/controlled-UAT customer-ID restriction.
- 幂等与事务 / Idempotency and transactions：首次创建返回 `201 + Location`；相同键与规范化请求哈希重放返回原订单 `200`。竞争失败候选通过事务回滚，不产生重复订单或 Outbox；异载荷返回 `409`。/ First create returns `201 + Location`; identical replay returns the original order with `200`. Losing candidates roll back transactionally and create neither duplicate orders nor Outbox rows; different payloads return `409`.
- 错误与隔离 / Errors and isolation：JSON/绑定失败为 RFC 9457 `400`；领域/字段语义错误（含重复 SKU）为 `422`；不存在和非所有者均为 `404`；状态、幂等和 `If-Match` 乐观锁冲突为 `409`。/ JSON/binding failures map to RFC 9457 `400`; semantic/domain violations including duplicate SKU map to `422`; missing and unauthorized resources both map to `404`; state, idempotency, and `If-Match` optimistic-lock conflicts map to `409`.
- 边界 / Boundary：响应只包含 UUID/opaque public IDs，不暴露 BIGINT；未实现认证、可信 Catalog 定价、Kafka publisher、前端或 Issues #7–#11，未部署、未自动合并。/ Responses expose only UUID/opaque public IDs, never BIGINTs; authentication, trusted Catalog pricing, Kafka publishing, frontend, and Issues #7–#11 remain out of scope, with no deployment or auto-merge.
- TDD / TDD：红灯契约提交 `6096e87` 先加入 PostgreSQL API 验收测试；随后以最小应用端口、事务编排和 Web adapter 转绿。/ Red contract commit `6096e87` added PostgreSQL API acceptance tests first; minimum application ports, transaction orchestration, and web adapter then made them green.
