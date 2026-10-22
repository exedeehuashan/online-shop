# TASK-0001：测试计划 / Test Plan

> 状态 / Status：`DESIGNED / PENDING_APPROVAL`。本计划描述未来实现的测试策略；当前尚未执行测试。执行须由 tester agent 在人类明确监督下进行。/ This plan describes future testing; no tests have been executed. Execution requires a tester agent under explicit human supervision.

## 原则

- 严格 TDD：先写失败测试，再写最小实现，最后重构。
- 测试行为和契约，避免绑定内部实现。
- 优先确定性测试：注入 Clock/ID，禁止固定 sleep。
- 真实 PostgreSQL 行为使用 Testcontainers，避免以内存替身掩盖差异；Kafka publisher 已延后，不属于 MVP 测试门禁。

## 测试金字塔

1. **领域单元测试（最多）**：Money、OrderItem、Order 不变量、状态机、幂等行为。
2. **应用组件测试**：用 fake ports 验证 handler 编排、事务结果和错误映射。
3. **适配器集成测试**：Flyway、repository、乐观锁、唯一键、双 ID/索引和同事务 Outbox 写入。
4. **API/契约测试**：OpenAPI、Problem Details、headers、JSON schema、事件兼容性。
5. **前端组件测试**：表单、状态、可访问性、TanStack Query 行为。
6. **Playwright E2E（少量）**：创建 -> 查看 -> 确认；创建 -> 取消；失败恢复。
7. **UAT**：按 User Story 的 Given/When/Then，在类生产 Compose 环境执行。
8. **压力测试**：读写混合、幂等重放、热点客户、并发状态冲突和稳定性。

## 需求追踪 / Requirements traceability

| Story | 主要层级 / Primary levels | Future issue |
|---|---|---|
| US-01 创建 / Create | domain, API, PostgreSQL, idempotency, E2E | #13, #4–#6, #9 |
| US-02 查询 / View | repository pagination, API ownership, UI/E2E | #14, #5–#6, #8–#9 |
| US-03 确认 / Confirm | state machine, optimistic lock, event contract | #15, #4, #6–#9 |
| US-04 取消 / Cancel | state machine, reason validation, event contract, UI | #16, #4, #6–#9 |
| US-05 质量资产 / Quality assets | integration, contracts, Playwright, UAT, performance | #17, #9–#11 |

## 核心测试矩阵

| 场景 | 层级 | 关键断言 |
|---|---|---|
| 创建合法订单 | unit/API/integration/E2E | 总额、DRAFT、201、唯一订单/Outbox 记录、快照不可变 |
| 空项/负价格/零数量/重复 SKU | unit/API | 422 且不持久化；不可解析请求为 400 |
| 幂等重放 | integration/API/perf | 200、同一订单/Outbox 记录、稳定响应 |
| 相同键不同载荷 | integration/API | 409 |
| 确认/取消状态机 | unit/API | 合法转换、终态幂等、非法 409 |
| 并发确认和取消 | integration/perf | 一个胜出，另一个 409，无丢失更新 |
| Outbox 原子写入 | integration | 订单与 Outbox 同成同败；本 MVP 不断言 Kafka 发布 |
| 时间有序内部 ID | unit/integration | 并发唯一、近似有序、node 冲突约束、时钟回拨 fail-closed |
| 客户隔离 | API/security | 非所有者得到 404 |
| 分页稳定性 | integration/API | 排序稳定、边界正确 |
| UI 主流程 | Playwright | 可访问交互、无脆弱等待 |

## 测试数据

- Builders/Object Mothers 生成订单；明确边界案例，不共享可变 fixture。
- 固定 clock 与 UUID provider，事件快照可重复。
- UAT/压力环境只使用合成客户、地址和商品。

## 压力测试草案

- 工具建议 k6（易容器化、JS 脚本）；如团队偏 Java 可选 Gatling。
- 工作负载：70% 列表/详情、20% 创建、5% 确认、5% 取消。
- 阶段：smoke -> baseline -> load -> spike -> soak。
- 初始目标：错误率 < 1%；读取 P95 < 300ms；写入 P95 < 500ms；无数据不变量破坏。目标需在确定硬件/并发后批准。
- 单独观测数据库连接、Outbox 未发布数量、JVM/GC；Kafka publish latency 延后到 publisher 增量。

## 执行门禁

- Gate 0: 人类批准 #2 并将 TASK-0001 由 `PENDING_APPROVAL` 转为 `APPROVED`；此前不得编写或执行实现测试。
- Gate 1: developer agent 按 TDD 提交失败测试与最小实现 PR；tester agent 独立审查验收映射。
- Gate 2: 禁止 developer/tester agent 自动合并、部署或使用生产数据；所有活动均需明确人类监督。

- PR：单元、组件、静态检查、架构规则。
- 合并前：PostgreSQL、Outbox 与 API/事件存储契约测试。
- 候选版本：Playwright E2E 和 UAT。
- 按需/夜间：压力与 soak。

## Issue #5 execution plan / Issue #5 执行计划

| Priority / 优先级 | Order / 顺序 | Dependency / 依赖 | Status / 状态 | Coverage / 覆盖 |
|---|---:|---|---|---|
| P0 | 3 | #2, #4 | IN_REVIEW / 审核中 | 空库 Flyway migration、约束与双索引 / empty-database migration, constraints, dual indexes |
| P0 | 3 | #2, #4 | IN_REVIEW / 审核中 | repository round-trip、数据库强制不可变快照、稳定分页 / repository round-trip, database-enforced immutable snapshots, stable paging |
| P0 | 3 | #2, #4 | IN_REVIEW / 审核中 | Hibernate/Flyway schema validate、事务安全 replay/conflict、并发幂等、乐观锁 / Hibernate/Flyway schema validation, transaction-safe replay/conflict, concurrent idempotency, optimistic locking |
| P0 | 3 | #2, #4 | IN_REVIEW / 审核中 | Order mutation + Outbox commit/rollback 原子性 / atomic commit and rollback |

执行命令 / Command：`mvn -B clean verify`。Testcontainers 使用 `postgres:16-alpine`；无 Docker daemon 时明确跳过并记录为环境阻塞，不将跳过误报为集成测试通过。/ Testcontainers uses `postgres:16-alpine`; when no Docker daemon exists it skips explicitly and is recorded as an environmental blocker, never reported as an integration-test pass.

## 退出标准

- 所有验收标准映射到自动化测试，关键路径全绿。
- 无 Critical/High 缺陷；Medium 有明确处置。
- Flyway 从空库迁移成功且 schema validate 通过。
- API/事件契约通过，主要错误分支已测试。
- 性能基线被记录，未出现数据损坏或资源持续泄漏。


## Issue #4 独立 QA 执行计划 / Independent QA execution plan (PR #19)

> 范围 / Scope：仅 `feat/issue-4-order-domain-application`（PR #19）的领域层与应用层。持久化、REST、Kafka、前端、Docker、UAT 与性能不在本次范围。/ Only the domain and application layers of PR #19; persistence, REST, Kafka, frontend, Docker, UAT, and performance are out of scope.

### 验证目标 / Verification objectives

| 目标 / Objective | 方法 / Method | 证据 / Evidence |
|---|---|---|
| Issue #4 验收标准 / Issue #4 acceptance criteria | 逐条对照代码与测试 / criterion-by-criterion review | 验收矩阵 / acceptance matrix |
| DDD 边界与不变量 / DDD boundaries and invariants | 聚合、值对象、端口审查 / aggregate, value-object, port review | 新增领域 QA 测试 / new domain QA tests |
| 状态机 / State machine | 穷举合法/非法/幂等转换 / legal, illegal, idempotent transitions | `OrderTest`, `OrderDomainQaTest` |
| 组合优于继承 / Composition over inheritance | 继承层级、final、端口注入审查 / hierarchy and injection review | 类声明审查 / class declaration review |
| 模块依赖方向 / Module dependency direction | ArchUnit + 手工 import 检查 / ArchUnit plus import review | `ArchitectureTest` |
| ID 生成器边界 / ID generator boundaries | epoch、node、sequence、回拨、63-bit 上限 / epoch, node, sequence, rollback, 63-bit max | `IdGeneratorQaTest` |
| 应用层编排与所有权 / Orchestration and ownership | 内存适配器全链路 + 跨客户访问 / in-memory full flow plus cross-customer access | `OrderApplicationQaTest` |
| 构建门禁 / Build gate | `mvn clean verify` | 构建日志 / build log |

### 退出标准 / Exit criteria

- `mvn clean verify` 通过；无 Critical/High 缺陷。/ `mvn clean verify` green; no Critical/High defects.
- 每个发现附可复现命令与期望/实际结果。/ Every finding has a reproducible command with expected/actual results.
- 未修改业务逻辑以迎合测试。/ No business logic changed to satisfy tests.
