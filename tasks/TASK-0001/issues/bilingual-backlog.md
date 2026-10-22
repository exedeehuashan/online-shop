# TASK-0001 bilingual GitHub backlog / 双语 GitHub 工作清单

This file is the source used to update GitHub issues. / 本文件是更新 GitHub Issues 的来源。

## #2 — [DESIGN / 设计] Approve order contracts and ADRs / 批准订单契约与 ADR

**Priority / 优先级:** P0
**Sequence / 顺序:** 0
**Dependencies / 依赖:** Design-only PR / 仅设计 PR
**Status / 状态:** PENDING_APPROVAL / 待批准；all implementation is blocked / 所有实施均被阻止

### Purpose / 目标
Review and approve TASK-0001 API, database, modular-monolith/hexagonal boundaries, event contracts, security constraints, and deferred scope. / 审查并批准 TASK-0001 的 API、数据库、模块化单体/六边形边界、事件契约、安全约束与延后范围。

### Acceptance Criteria / 验收标准
- [x] Human approver records the seven design choices in `design.md`. / 人类批准者已记录 `design.md` 中七项设计选择。
- [ ] Human approver explicitly approves PR #18 as the implementation baseline. / 人类批准者明确批准 PR #18 作为实施基线。
- [ ] API, schema/Flyway, auth limitation, outbox/Kafka, and Redis/Elasticsearch deferral are explicitly accepted or revised. / 明确接受或修订 API、schema/Flyway、认证限制、Outbox/Kafka 及 Redis/Elasticsearch 延后策略。
- [ ] `status.json` changes to `APPROVED` only after approval. / 仅在批准后把 `status.json` 改为 `APPROVED`。
- [ ] Developer/tester agents remain blocked until explicit human authorization. / developer/tester agents 在人类明确授权前保持阻塞。

## #3 — [EPIC / 史诗] Order module MVP / 订单模块 MVP

**Priority / 优先级:** P0
**Sequence / 顺序:** 1–7
**Dependencies / 依赖:** #2, then stories #13–#17 and tasks #4–#11 / 先 #2，再执行故事 #13–#17 与任务 #4–#11
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Outcome / 结果
Deliver an electronics-shop Order bounded context for creating, viewing, confirming, and cancelling orders, with reliable events and layered quality assets. / 交付电子商城订单限界上下文，支持创建、查询、确认、取消、可靠事件与分层质量资产。

### Acceptance Criteria / 验收标准
- [ ] Stories #13–#17 are accepted. / 故事 #13–#17 验收通过。
- [ ] Tasks #4–#11 pass their gates and tests. / 任务 #4–#11 通过门禁与测试。
- [ ] Final docs match implementation; no direct-main commit, auto-merge, deployment, or production change. / 最终文档与实现一致；无直接 main 提交、自动合并、部署或生产变更。

### Roadmap / 路线图
`#2 -> #13–#17 -> #4 -> #5 -> (#6,#7) -> #8 -> #9 -> #10`; #11 integrates approved deliverables. #1 is independent Catalog scope; #12 is a closed duplicate of #11. / #1 是独立商品目录范围；#12 是 #11 的已关闭重复项。

## #4 — [BACKEND / 后端] Bootstrap order domain and application layer / 建立订单领域与应用层

**Priority / 优先级:** P0
**Sequence / 顺序:** 2
**Dependencies / 依赖:** #2; business rules from #13–#16 / #2；业务规则来自 #13–#16
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Use TDD for pure-Java aggregate, value objects, state machine, use-case ports, and errors. / 以 TDD 实现纯 Java 聚合、值对象、状态机、用例端口和错误。
- [ ] Domain has no Spring/JPA/Kafka dependency; use composition over inheritance. / 领域层不依赖 Spring/JPA/Kafka；组合优于继承。
- [ ] Inject Clock, public UUID generator, 63-bit time-ordered internal ID generator, and repositories through ports; ArchUnit enforces direction. / 通过端口注入时钟、公开 UUID 生成器、63-bit 时间有序内部 ID 生成器与仓储；ArchUnit 强制依赖方向。
- [ ] Work is performed by a developer agent under explicit human supervision. / 由 developer agent 在人类明确监督下执行。

## #5 — [DATABASE / 数据库] Add PostgreSQL persistence and Flyway migrations / 添加 PostgreSQL 持久化与 Flyway 迁移

**Priority / 优先级:** P0
**Sequence / 顺序:** 3
**Dependencies / 依赖:** #2, #4
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Approved schema migrates from an empty PostgreSQL database with Flyway; ORM only validates. / 批准后的 schema 可由 Flyway 从空 PostgreSQL 迁移；ORM 仅校验。
- [ ] Testcontainers covers constraints, repository behavior, paging, idempotency, and optimistic locking. / Testcontainers 覆盖约束、仓储、分页、幂等和乐观锁。
- [ ] Order mutation and Outbox insert share one transaction; Order owns its tables. / 订单变更与 Outbox 写入同一事务；Order 独占其表。
- [ ] Large tables use BIGINT internal PKs plus UUID/opaque public references and dual indexes without PostgreSQL extensions. / 大表使用 BIGINT 内部主键、UUID/opaque 对外引用和双索引，且不依赖 PostgreSQL 扩展。
- [ ] Customer/product/address/price snapshots are immutable and avoid cross-context joins. / 客户、商品、地址、价格快照不可变，并避免跨上下文 JOIN。
- [ ] TDD and developer-agent human supervision are required. / 必须 TDD，并由人类监督 developer agent。

## #6 — [BACKEND / 后端] Implement versioned order REST API / 实现版本化订单 REST API

**Priority / 优先级:** P0
**Sequence / 顺序:** 4
**Dependencies / 依赖:** #2, #4, #5; stories #13–#16
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Approved create/list/detail/confirm/cancel endpoints and OpenAPI are implemented. / 实现批准后的创建、列表、详情、确认、取消端点与 OpenAPI。
- [ ] First create returns 201; identical idempotent replay returns 200; semantic violations return 422 and parse failures return 400. / 首次创建返回 201；相同幂等重放返回 200；语义违规返回 422，解析失败返回 400。
- [ ] Duplicate SKU is rejected by the order API with 422; internal BIGINT IDs are never exposed. / 订单 API 以 422 拒绝重复 SKU；内部 BIGINT ID 不得暴露。
- [ ] Missing and unauthorized orders both return 404; customer ID is dev/UAT-only until auth exists. / 不存在与越权订单均返回 404；认证前 customer ID 仅限开发/UAT。
- [ ] API tests cover success, validation, replay, ownership, and conflict. / API 测试覆盖成功、校验、重放、所有权和冲突。

## #7 — [OUTBOX / 事务事件] Persist order events transactionally / 以事务方式持久化订单事件

**Priority / 优先级:** P1
**Sequence / 顺序:** 4 (parallel after #5) / 4（#5 后可并行）
**Dependencies / 依赖:** #2, #5
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Persist versioned Created/Confirmed/Cancelled envelopes in Outbox in the same transaction as the order mutation. / 与订单变更同一事务持久化版本化 Created/Confirmed/Cancelled Outbox envelope。
- [ ] Test rollback atomicity, event ID uniqueness, aggregate ordering metadata, and immutable payload snapshots with PostgreSQL Testcontainers. / 使用 PostgreSQL Testcontainers 测试回滚原子性、事件 ID 唯一性、聚合顺序元数据与不可变 payload 快照。
- [ ] Kafka publisher, retry/delivery semantics, broker tests, and consumer idempotency are explicitly deferred to a new increment. / Kafka publisher、重试/投递语义、broker 测试和消费幂等明确延后到新增量。

## #8 — [FRONTEND / 前端] Build React order workflows / 构建 React 订单流程

**Priority / 优先级:** P1
**Sequence / 顺序:** 5
**Dependencies / 依赖:** #6; stories #13–#16
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] React/TypeScript + TanStack Router/Query supports create, list, detail, confirm, and cancel. / React/TypeScript + TanStack Router/Query 支持创建、列表、详情、确认和取消。
- [ ] Loading, empty, validation, conflict, retry, and accessibility states are tested first. / 测试先行覆盖加载、空、校验、冲突、重试与可访问性状态。
- [ ] API client is centralized/generated; fixtures use composition. / API 客户端集中维护或生成；fixture 使用组合。
- [ ] Cart/review UI merges duplicate SKU quantities before submission; the order API still rejects duplicate SKU. / 购物车/提交页在发送前合并重复 SKU 数量；订单 API 仍拒绝重复 SKU。
- [ ] No production exposure without authentication. / 无认证不得公开生产使用。

## #9 — [TEST / 测试] Add integration, contract and Playwright suites / 添加集成、契约与 Playwright 套件

**Priority / 优先级:** P1
**Sequence / 顺序:** 6
**Dependencies / 依赖:** #6, #7, #8
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Dedicated `tests/integration`, `tests/contracts`, and `tests/e2e` directories have documented commands. / 独立目录具有文档化命令。
- [ ] Suites cover API, persisted Outbox envelopes, concurrent conflicts, snapshot immutability, dual IDs, and core UI journeys. / 覆盖 API、持久化 Outbox envelope、并发冲突、快照不可变、双 ID 和核心 UI 流程。
- [ ] Playwright uses stable selectors and no fixed sleeps; data is deterministic and synthetic. / Playwright 使用稳定 selector、无固定 sleep；数据确定且合成。
- [ ] A tester agent executes only after approval under human supervision. / tester agent 仅在批准后、在人类监督下执行。

## #10 — [TEST / 测试] Add UAT and performance assets / 添加 UAT 与性能资产

**Priority / 优先级:** P2
**Sequence / 顺序:** 7
**Dependencies / 依赖:** #9; stories #13–#17
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Dedicated `tests/uat` and `tests/performance` contain READMEs and unified commands. / 独立目录包含 README 与统一命令。
- [ ] UAT maps Given/When/Then to stories; k6 or approved Gatling covers mix, replay, hotspot, and conflicts. / UAT 映射故事；k6 或批准后的 Gatling 覆盖混合、重放、热点与冲突。
- [ ] Reports include P95, errors, resources, and Outbox backlog; Kafka metrics wait for the publisher increment. / 报告含 P95、错误、资源与 Outbox 积压；Kafka 指标等待 publisher 增量。
- [ ] Thresholds require environment-baseline approval. / 阈值需基准环境批准。

## #11 — [DEVOPS / 工程] Add Docker Compose and CI quality gates / 添加 Docker Compose 与 CI 质量门禁

**Priority / 优先级:** P1
**Sequence / 顺序:** 7 (integration task) / 7（集成任务）
**Dependencies / 依赖:** #5–#10 as applicable / 按适用范围依赖 #5–#10
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] Multi-stage, non-root, health-checked backend/frontend images and local Compose exist. / 提供多阶段、非 root、有健康检查的镜像与本地 Compose。
- [ ] PostgreSQL is the default dependency; Kafka, Redis, and Elasticsearch are optional profiles only. / PostgreSQL 是默认依赖；Kafka、Redis、Elasticsearch 仅可选 profile。
- [ ] CI gates unit/static/architecture before integration/contracts and E2E/UAT; performance is manual/nightly. / CI 先单元/静态/架构，再集成/契约和 E2E/UAT；性能按需/夜间。
- [ ] No Kubernetes, deployment, production change, auto-merge, or direct-main commit. / 不含 Kubernetes、部署、生产变更、自动合并或直接 main 提交。

## #12 — [DUPLICATE / 重复] Docker Compose and CI quality gates / Docker Compose 与 CI 质量门禁

**Priority / 优先级:** P1
**Sequence / 顺序:** N/A / 不适用
**Dependencies / 依赖:** Duplicate of / 重复于 #11
**Status / 状态:** CLOSED — DUPLICATE / 已关闭—重复

### Resolution / 处理结果
This issue duplicates #11. Keep all discussion, dependencies, and acceptance criteria on #11; no implementation is authorized here. / 本 Issue 与 #11 重复。所有讨论、依赖和验收标准集中到 #11；本 Issue 不授权任何实现。

### Acceptance Criteria / 验收标准
- [x] Duplicate status and canonical issue #11 are documented in both languages. / 已用双语记录重复状态与规范 Issue #11。
- [x] Issue remains closed and creates no independent work. / Issue 保持关闭且不产生独立工作。

## #13 — [STORY / 故事] Create an order safely / 安全创建订单

**Priority / 优先级:** P0
**Sequence / 顺序:** 1
**Dependencies / 依赖:** #2 approval; implemented by #4–#6 / #2 批准；由 #4–#6 实现
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

**As a / 作为** customer / 客户, **I want / 我希望** to create an order from product and shipping snapshots / 根据商品和收货快照创建订单, **so that / 从而** I can review a stable total before confirmation / 在确认前查看稳定总额。

### Acceptance Criteria / 验收标准
- [ ] Valid non-empty NZD items create one DRAFT order with server-calculated line totals and total. / 合法非空 NZD 商品创建一个 DRAFT 订单，行小计与总额由服务端计算。
- [ ] Same customer/key/payload replay returns 200 with the original order and no duplicate Outbox row; different payload returns 409. / 同客户/键/载荷重放返回 200 与原订单，且不重复写 Outbox；不同载荷返回 409。
- [ ] Semantic errors, including duplicate SKU, return 422 and write nothing; unparseable requests return 400. / 包含重复 SKU 的语义错误返回 422 且不写数据；无法解析的请求返回 400。
- [ ] Client price snapshot risk is visibly limited to dev/UAT until approved Catalog integration. / 客户端价格快照风险明确限制在开发/UAT，直至批准 Catalog 集成。
- [ ] Customer, address, product-name, and unit-price snapshots are persisted immutably. / 客户、地址、商品名称与单价快照被不可变地持久化。

## #14 — [STORY / 故事] View my orders / 查看我的订单

**Priority / 优先级:** P0
**Sequence / 顺序:** 1
**Dependencies / 依赖:** #2 approval; implemented by #5, #6, #8 / #2 批准；由 #5、#6、#8 实现
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

**As a / 作为** customer / 客户, **I want / 我希望** to list and inspect my orders / 列出并查看我的订单, **so that / 从而** I can understand their contents and status / 了解内容与状态。

### Acceptance Criteria / 验收标准
- [ ] List is customer-scoped, stable, newest-first, and paginated. / 列表按客户隔离、稳定、最新优先并分页。
- [ ] Detail shows immutable customer/item/address/price snapshots, total, status, and version using public IDs only. / 详情仅使用 public IDs，并显示不可变客户/商品/地址/价格快照、总额、状态与版本。
- [ ] Missing or non-owned order returns indistinguishable 404; no sensitive data leaks. / 不存在或非本人订单统一 404，不泄露敏感数据。
- [ ] UI covers loading, empty, error, and retry states. / UI 覆盖加载、空、错误与重试状态。

## #15 — [STORY / 故事] Confirm a draft order / 确认草稿订单

**Priority / 优先级:** P0
**Sequence / 顺序:** 1
**Dependencies / 依赖:** #2 approval; implemented by #4, #6–#8 / #2 批准；由 #4、#6–#8 实现
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] DRAFT becomes CONFIRMED and emits one versioned confirmation event. / DRAFT 变为 CONFIRMED 并产生一个版本化确认事件。
- [ ] Re-confirming CONFIRMED is idempotent with no new event; CANCELLED returns 409. / 重复确认 CONFIRMED 幂等且无新事件；CANCELLED 返回 409。
- [ ] Stale concurrent version returns 409 without lost update. / 过期并发版本返回 409，且无更新丢失。
- [ ] UI communicates success and conflict accessibly. / UI 以可访问方式呈现成功与冲突。

## #16 — [STORY / 故事] Cancel an eligible order / 取消可取消订单

**Priority / 优先级:** P0
**Sequence / 顺序:** 1
**Dependencies / 依赖:** #2 approval; implemented by #4, #6–#8 / #2 批准；由 #4、#6–#8 实现
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

### Acceptance Criteria / 验收标准
- [ ] DRAFT or CONFIRMED becomes CANCELLED with a validated reason and one event. / DRAFT 或 CONFIRMED 以合法原因变为 CANCELLED 并产生一个事件。
- [ ] Re-cancelling CANCELLED is idempotent with no new event. / 重复取消 CANCELLED 幂等且无新事件。
- [ ] CANCELLED cannot return to another state; concurrent conflict returns 409. / CANCELLED 不可回到其他状态；并发冲突返回 409。
- [ ] UI preserves user input on validation/conflict and announces result accessibly. / UI 在校验/冲突时保留输入并可访问地提示结果。

## #17 — [STORY / 故事] Verify order quality independently / 独立验证订单质量

**Priority / 优先级:** P1
**Sequence / 顺序:** 1, executed at 6–7 / 1，执行于 6–7
**Dependencies / 依赖:** #2 approval; delivered by #9–#11 / #2 批准；由 #9–#11 交付
**Status / 状态:** BLOCKED — PENDING_APPROVAL / 已阻塞—待批准

**As a / 作为** tester / 测试人员, **I want / 我希望** independently runnable quality assets / 可独立运行的质量资产, **so that / 从而** each story and contract can be verified repeatably / 可重复验证每个故事与契约。

### Acceptance Criteria / 验收标准
- [ ] Integration, contracts, Playwright E2E, UAT, and performance use dedicated directories and documented commands. / 集成、契约、Playwright E2E、UAT、性能使用独立目录与文档化命令。
- [ ] Every #13–#16 criterion maps to an automated test or explicit human UAT step. / #13–#16 每项标准映射自动化测试或明确人工 UAT 步骤。
- [ ] Fixtures are deterministic/synthetic; reports are reproducible and contain no secrets. / fixture 确定且合成；报告可复现且无秘密。
- [ ] Tester-agent execution requires explicit human supervision after design approval. / 设计批准后 tester agent 执行仍需人类明确监督。
