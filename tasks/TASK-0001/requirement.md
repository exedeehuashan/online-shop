# TASK-0001：E-Shop 订单模块需求 / Order Module Requirements

> 状态 / Status：`DESIGNED / PENDING_APPROVAL`。本文件仅定义需求，不授权开发、测试执行、部署或生产变更。/ This document defines requirements only; it does not authorize development, test execution, deployment, or production changes.

## 基线协调 / Baseline reconciliation

- Issue #1 是独立的商品目录上下文（Java 17、Spring Boot 3、Maven、feature branch + PR），不得改写、关闭或并入本订单任务。/ Issue #1 is the independent Catalog context (Java 17, Spring Boot 3, Maven, feature branch + PR) and must not be rewritten, closed, or absorbed into this Order task.
- 订单模块沿用其工程基线：Java 17、Spring Boot 3、Maven、PostgreSQL、Flyway；所有后续实现必须在功能分支上通过 PR 审核，禁止直接提交 `main`。/ Order adopts the same engineering baseline: Java 17, Spring Boot 3, Maven, PostgreSQL, and Flyway; all later implementation must use a feature branch and reviewed PR, never direct commits to `main`.
- Catalog 与 Order 是两个限界上下文；本阶段只设计 Order，不实现 Catalog，也不依赖 #1 完成。/ Catalog and Order are separate bounded contexts; this stage designs Order only and neither implements Catalog nor depends on #1 completing.

## 目标

建设一个面向电子商品（手机、电脑及外设）的订单模块，以模块化单体作为首期交付形态，为后续自动化测试和微服务拆分建立稳定边界。

## 范围（MVP）

- 创建订单：提交客户、收货地址与一个或多个商品快照。
- 查询订单详情与订单列表（按客户分页）。
- 确认订单、取消订单。
- 订单总价由服务端根据订单项快照计算。
- 订单生命周期、领域规则、持久化和领域事件。
- React 订单创建、订单列表、订单详情界面。
- PostgreSQL + Flyway；Docker Compose 本地运行。
- 单元、组件/集成、API、端到端、UAT、压力测试的目录与基线。
- Transactional Outbox 的可靠事件落库；Kafka 发布器延后到下一增量。

## 非范围（MVP）

- 商品目录管理、库存预占/扣减、支付、退款、物流履约。
- 用户注册、登录、认证和授权（首期用可信 `customerId`；上线前必须补齐）。
- 优惠券、促销、税务、多币种、拆单、合单、退换货。
- 管理后台、推荐/搜索商品、Elasticsearch 订单检索。
- Kubernetes 清单和生产部署。
- 真正的微服务拆分及跨服务分布式事务。

## 分阶段范围 / Phased scope

| 阶段 / Phase | 范围 / Scope | 决策 / Decision |
|---|---|---|
| MVP | Order 领域、REST、PostgreSQL/Flyway、Outbox/Kafka、React/TanStack、Docker、分层测试资产 | 本设计批准后才可启动 / Starts only after design approval |
| 可选本地 profile / Optional local profile | Redis、Elasticsearch 容器 | 可验证连接但不进入业务关键路径 / Connectivity only, not business-critical path |
| 延后 / Deferred | Redis 缓存、Elasticsearch 订单搜索、认证授权、Catalog 可信定价、Kubernetes、微服务拆分 | 由独立 ADR/Issue 与风险评估启动 / Requires separate ADR/issue and risk review |

## 假设与约束

- 首期币种固定为 `NZD`；金额以 `numeric(19,2)` 持久化，Java 使用 `BigDecimal`。
- 商品名称、SKU、单价在下单时固化为订单项快照；由于商品模块不在范围内，MVP 请求暂时携带该快照。
- 客户端价格快照仅临时用于受控开发/UAT；公开或生产使用前必须由 Catalog/定价端口提供可信快照。
- 对外 API 使用不可推断的 UUID/opaque string；大型表同时保存应用生成的 63-bit 时间有序内部 ID，用于主键、关联、范围查询和归档。MVP 不依赖 PostgreSQL 扩展。
- 订单保存客户名称、商品名称/单价等成交时快照；快照创建后不可随源数据变化，避免跨上下文 JOIN。
- 同一客户使用 `Idempotency-Key` 创建订单，重复请求返回原订单。
- 所有时间为 UTC，API 使用 ISO-8601。
- 订单项数量必须为正整数；单价不得为负；订单至少包含一项。购物车/提交页可在本地合并重复 SKU；订单创建/结算 API 必须拒绝重复 SKU。
- 首期不承诺库存和支付成功，状态只表达订单自身生命周期。
- 遵循 TDD、DDD、Favor composition over inheritance；领域层不依赖 Spring/JPA/Kafka。

## 功能性需求

| 编号 | 需求 |
|---|---|
| FR-01 | 客户可通过幂等请求创建订单。 |
| FR-02 | 系统校验请求并计算每项小计及订单总额。 |
| FR-03 | 客户可查看自己的订单列表及详情。 |
| FR-04 | `DRAFT` 订单可被确认为 `CONFIRMED`。 |
| FR-05 | `DRAFT` 或 `CONFIRMED` 订单可被取消为 `CANCELLED`。 |
| FR-06 | 非法状态转换返回明确的领域冲突错误。 |
| FR-07 | 创建、确认、取消产生可可靠投递的领域事件。 |
| FR-08 | 写操作支持乐观锁，避免并发状态覆盖。 |

## 非功能性需求

- API 错误采用 RFC 9457 Problem Details 风格。
- P95 查询延迟目标（本地基准环境）：读取 < 300ms，写入 < 500ms；压测阈值最终由测试环境基线确认。
- 数据库迁移可重复验证，不允许 ORM 自动改表。
- 日志携带 correlation ID、order ID（存在时），禁止记录完整地址等敏感信息。
- 可观测性预留 Spring Boot Actuator、Micrometer 指标和 OpenTelemetry 接入点。
- 依赖通过端口/适配器组合，避免领域对象继承基础实体。

## User Stories 与验收标准

### US-01 创建订单

作为客户，我希望提交商品和收货信息创建订单，以便后续确认购买。

- Given 合法且至少一项商品；When 创建订单；Then 返回 `201`、`DRAFT` 订单及正确总额。
- Given 相同客户、相同 `Idempotency-Key` 和相同请求；When 重试；Then 返回 `200` 与原订单，且不重复入库/写 Outbox。
- Given 同一键但载荷不同；Then 返回 `409`。
- Given 语法正确但数量、价格、币种、重复 SKU 或订单项不合法；Then 返回 `422` 且无数据写入。无法解析的请求仍返回 `400`。

### US-02 查询订单

作为客户，我希望查看自己的订单，以便确认当前内容与状态。

- 按客户查询列表，支持 `page/size`、创建时间倒序。
- 存在且属于该客户时返回详情；不存在或不可见时返回 `404`，避免越权信息泄露。

### US-03 确认订单

作为客户，我希望确认草稿订单，以表达购买意图。

- `DRAFT -> CONFIRMED` 成功并发布 `OrderConfirmed`。
- 已确认订单再次确认应幂等返回当前资源；其他终态返回 `409`。
- 并发更新使用版本号，冲突返回 `409`。

### US-04 取消订单

作为客户，我希望取消尚未履约的订单。

- `DRAFT|CONFIRMED -> CANCELLED`，记录取消原因并发布 `OrderCancelled`。
- 已取消订单再次取消应幂等返回当前资源。
- 不允许从 `CANCELLED` 返回其他状态。

### US-05 自动化验收

作为测试人员，我希望测试资产按类型隔离且可在容器环境执行。

- 集成、UAT、压力和 Playwright 脚本有独立目录、README 与统一命令入口。
- CI 可先执行单元测试，再执行集成/API，最后按需执行 E2E/UAT/压力测试。

## 待用户批准（实施门禁）

以下设计涉及公开 API、数据库 schema 与未来服务边界，进入开发前需要明确批准：

1. REST API 路径与错误/幂等语义。
2. PostgreSQL 表结构和 Flyway 迁移策略。
3. 模块化单体边界、Transactional Outbox 设计及未来微服务拆分边界；Kafka publisher 本增量延后。
4. MVP 暂不实现认证、Redis 缓存和 Elasticsearch 索引的决定。
5. 对外 UUID/opaque ID、内部时间有序 ID、双索引和快照冗余策略，以及临时未认证 customer reference 的安全约束。
6. 临时客户端价格快照以及接入可信 Catalog/定价端口前不得公开上线的限制。

任何开发或测试执行只能由后续 developer/tester agents 在人类明确批准和监督下进行。设计批准前，所有实施 Issue 保持 `Blocked / PENDING_APPROVAL`。/ Any development or test execution must be performed later by developer/tester agents under explicit human approval and supervision. Until approval, all implementation issues remain blocked.

