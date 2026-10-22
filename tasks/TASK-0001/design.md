# TASK-0001：订单模块设计草案 / Order Module Design Draft

> 状态 / Status：`DESIGNED / PENDING_APPROVAL`。本文是设计输入，不代表已批准的公开契约，也不授权实现。/ This is design input, not an approved public contract or implementation authorization.

## 0. 技术与交付基线 / Technical and delivery baseline

- Backend: Java 17, Spring Boot 3, Maven; PostgreSQL + Flyway（不混用 Liquibase）。
- Frontend: React + TypeScript, TanStack Router/Query; Playwright for browser acceptance.
- Infrastructure: PostgreSQL Transactional Outbox in MVP; Kafka publisher, Redis, and Elasticsearch business use are deferred; Docker for local/review environments; Kubernetes deferred.
- Issue #1 remains the separate Catalog bounded context and retains its feature-branch + PR workflow. This Order design neither changes #1 nor creates a dependency on it.
- Future development/testing is reserved for developer/tester agents after explicit human approval; every change uses a feature branch and PR. No direct `main` commits, auto-merge, deployment, or production change.

## 1. 架构选择

首期采用**模块化单体 + 六边形架构**。一个可部署后端中保持清晰模块边界，优先获得简单事务、快速 TDD 和低运维成本；通过端口、事件契约和独立 schema ownership，保留微服务拆分能力。

```text
React Web
   |
REST /api/v1
   |
order interfaces -> application -> domain
                         |          (pure Java)
                         v
                    outbound ports
                    /     |       \
              PostgreSQL Outbox  future adapters
                              |
                    future Kafka publisher
```

依赖方向：`interfaces -> application -> domain`；`infrastructure` 实现 domain/application 定义的端口。使用组合对象（Money、Address、OrderItem）而非继承式基础实体。

## 2. 限界上下文与边界

### Order（本次实现）

- 拥有订单、订单项、地址快照、金额、状态转换和领域事件。
- 不直接读取其他模块的表。

### Catalog（外部/未来）

- 拥有 SKU、商品名称和当前售价。
- MVP 临时由调用方提交商品快照；仅用于受控开发/UAT。下一阶段以可信 `ProductSnapshotProvider`/定价端口替换，订单仍永久保存成交快照。

### Inventory / Payment / Fulfillment / Identity（未来）

- 仅通过显式命令/API 和版本化事件协作。
- Order 不继承或共享这些上下文的实体。

### 上下文关系 / Context relationships

| 上下文 / Context | Order 目前交互 / Current interaction | 微服务接缝 / Extraction seam |
|---|---|---|
| Catalog | MVP 接收不可信商品快照；仅限开发/验收 / accepts an untrusted snapshot for dev/UAT only | `ProductSnapshotProvider` port or versioned API; Order retains its immutable snapshot |
| Identity | 无集成；受信 fixture 提供 `customerId` / no integration; trusted fixture supplies ID | authenticated principal mapped at web adapter; domain stays identity-provider agnostic |
| Inventory | 无 / none | reserve/release commands and versioned events; no shared tables |
| Payment | 无 / none | payment intent/result events; Order state policy composes outcomes |
| Fulfillment | 无 / none | order-ready/cancelled events; independent lifecycle and database |

Order 独占 `orders`、`order_items`、`order_idempotency` 和 `outbox_events`。其他上下文不得直接读写这些表。/ Order exclusively owns these tables; other contexts must not read or write them directly.

## 3. 领域模型

### 聚合根 `Order`

- `OrderId`（对外 UUID）与 `OrderInternalId`（内部 63-bit 时间有序 ID；不出现在公开契约）
- `CustomerReference`（对外 UUID 或 opaque string，最长 128；不假设可解析）与可空的未来 `customerInternalId`
- `CustomerNameSnapshot`（创建时快照，不随 Identity 主数据变化）
- `status: OrderStatus`
- `items: List<OrderItem>`
- `shippingAddress: AddressSnapshot`
- `total: Money`
- `cancellationReason?`
- `createdAt`, `updatedAt`
- `version`（乐观锁）

### 值对象/实体

- `OrderItem`：内部时间有序 `orderItemId`、对外 `productReference`、可空的未来 `productInternalId`、`sku`、不可变 `productNameSnapshot`、`unitPriceSnapshot: Money`、`quantity`、`lineTotal`。
- `Money`：`amount + currency`，封装加法/乘法与舍入规则。
- `AddressSnapshot`：收件人、地址行、城市、地区、邮编、国家代码、电话（敏感字段日志脱敏）。
- 应用服务组合 `Clock`、`IdGenerator`、`OrderRepository`、`OutboxRepository` 等接口，便于确定性测试。

### 不变量

- 订单至少一项；订单创建/结算 API 中重复 SKU 必须拒绝并返回 `422 DUPLICATE_SKU`。购物车/提交页只在发送 API 前合并数量；Order 后端不承担购物车职责。
- 所有订单项币种一致且为 NZD；总额等于行小计之和。
- 客户、地址、商品名称和单价均为成交时不可变快照；之后源系统变更不得回写历史订单。更正只能通过显式审计用例，MVP 不提供该用例。
- 状态变化只能调用领域行为，禁止任意 setter。
- 状态更新与 Outbox 写入同一个 PostgreSQL 事务。

## 4. 状态机

| 当前状态 | 命令 | 下一状态 | 规则 |
|---|---|---|---|
| 无 | Create | DRAFT | 请求合法且幂等键未被不同载荷占用 |
| DRAFT | Confirm | CONFIRMED | 成功产生 `OrderConfirmed` |
| DRAFT | Cancel | CANCELLED | 需取消原因 |
| CONFIRMED | Confirm | CONFIRMED | 幂等，无新事件 |
| CONFIRMED | Cancel | CANCELLED | MVP 允许；支付接入后规则重审 |
| CANCELLED | Cancel | CANCELLED | 幂等，无新事件 |
| CANCELLED | Confirm | — | `409 ORDER_STATE_CONFLICT` |

未来支付/履约状态不直接塞入订单枚举；优先在各自上下文管理，然后通过策略组合决定订单可执行操作。

## 5. 应用用例

- `CreateOrderCommand` / `CreateOrderHandler`
- `GetOrderQuery` / `GetOrderHandler`
- `ListCustomerOrdersQuery` / handler
- `ConfirmOrderCommand` / handler
- `CancelOrderCommand` / handler
- Handler 负责事务与端口编排；业务不变量留在聚合根。

## 6. REST API 草案（待批准）

基础路径：`/api/v1/customers/{customerId}/orders`

| 方法 | 路径 | 行为 | 成功响应 |
|---|---|---|---|
| POST | `/api/v1/customers/{customerId}/orders` | 创建；必需 `Idempotency-Key` | 首次 `201` + Location；相同载荷幂等重放 `200` |
| GET | `/api/v1/customers/{customerId}/orders/{orderId}` | 获取详情 | `200` |
| GET | `/api/v1/customers/{customerId}/orders?page=0&size=20` | 分页列表 | `200` |
| POST | `.../{orderId}/confirmation` | 确认 | `200` |
| POST | `.../{orderId}/cancellation` | 取消，body 含 reason | `200` |

创建请求示意：

```json
{
  "customerName": "Example Customer",
  "items": [
    {"productId": "uuid", "sku": "PHONE-001", "name": "Phone", "unitPrice": {"amount": "999.00", "currency": "NZD"}, "quantity": 1}
  ],
  "shippingAddress": {
    "recipient": "Example", "line1": "1 Example St", "city": "Auckland",
    "region": "Auckland", "postalCode": "1010", "countryCode": "NZ"
  }
}
```

响应包括 `id, customerId, customerName, status, items, total, shippingAddress, version, createdAt, updatedAt`；仅暴露 public IDs，不暴露内部 BIGINT IDs。并发命令建议使用 `If-Match: "<version>"`。

错误使用 `application/problem+json`，字段至少含 `type,title,status,detail,instance,code,violations,correlationId`。映射：JSON/类型等语法解析失败 `400`；语法正确但违反字段或领域约束（含重复 SKU）统一 `422`；未找到 `404`；状态/版本/幂等键异载荷冲突 `409`。

### API 与认证影响 / API and authentication impact

- MVP 的路径级 `customerId` 仅用于本地开发和受控 UAT；Web adapter 必须把它视为不可信输入。/ Path-level `customerId` is only for local development and controlled UAT and is untrusted input.
- 在公开或生产环境启用前，必须接入认证，把 principal 的 subject 映射为 customer ID，并拒绝 path/principal 不一致。/ Before any public or production use, authentication must map the principal subject to customer ID and reject mismatches.
- 为避免枚举他人订单，所有“不存在或不属于当前客户”统一返回 `404`；日志不得记录完整地址、电话、token 或幂等请求正文。/ Missing and unauthorized orders both return `404`; sensitive fields must not be logged.
- 认证接入属于 Web adapter；Order domain 不依赖 Spring Security。/ Authentication belongs in the web adapter; the Order domain remains independent of Spring Security.

## 7. PostgreSQL / Flyway schema 草案（待批准）

```text
orders
  internal_id bigint PK                # 应用生成的 63-bit 时间有序 ID
  public_id uuid NOT NULL UNIQUE        # API orderId
  customer_ref varchar(128) NOT NULL    # UUID/opaque string，原样保存
  customer_internal_id bigint NULL      # 未来 Identity 映射；MVP 不填充/不做 FK
  customer_name_snapshot varchar(200) NOT NULL
  status varchar(32) NOT NULL CHECK (...)
  currency char(3) NOT NULL
  total_amount numeric(19,2) NOT NULL CHECK (total_amount >= 0)
  recipient varchar(200) NOT NULL
  address_line1 varchar(200) NOT NULL
  address_line2 varchar(200) NULL
  city varchar(100) NOT NULL
  region varchar(100) NULL
  postal_code varchar(32) NOT NULL
  country_code char(2) NOT NULL
  phone varchar(32) NULL
  cancellation_reason varchar(500) NULL
  version bigint NOT NULL
  created_at timestamptz NOT NULL
  updated_at timestamptz NOT NULL

order_items
  internal_id bigint PK
  order_internal_id bigint NOT NULL FK orders(internal_id)
  product_ref varchar(128) NOT NULL
  product_internal_id bigint NULL       # 未来 Catalog 映射；MVP 不填充/不做 FK
  sku varchar(100) NOT NULL
  product_name_snapshot varchar(300) NOT NULL
  unit_amount_snapshot numeric(19,2) NOT NULL
  currency char(3) NOT NULL
  quantity integer NOT NULL CHECK (quantity > 0)
  line_total numeric(19,2) NOT NULL
  UNIQUE(order_internal_id, sku)

order_idempotency
  customer_ref varchar(128) NOT NULL
  idempotency_key varchar(128) NOT NULL
  request_hash char(64) NOT NULL
  order_internal_id bigint NOT NULL FK orders(internal_id)
  created_at timestamptz NOT NULL
  PRIMARY KEY(customer_ref, idempotency_key)

outbox_events
  internal_id bigint PK
  event_id uuid NOT NULL UNIQUE
  aggregate_type varchar(100) NOT NULL
  aggregate_public_id uuid NOT NULL
  aggregate_internal_id bigint NOT NULL
  event_type varchar(150) NOT NULL
  event_version integer NOT NULL
  payload jsonb NOT NULL
  occurred_at timestamptz NOT NULL
  published_at timestamptz NULL         # 为未来 publisher 保留，本增量不更新
  attempts integer NOT NULL DEFAULT 0   # 为未来 publisher 保留
```

ID 实施方案：应用层 `TimeOrderedIdGenerator` 生成正数 63-bit ID（时间戳 + 配置的 node id + 毫秒内 sequence），通过端口注入并用固定时钟测试；部署必须为每个并发节点分配唯一 node id，时钟回拨时 fail-closed。它不依赖 PostgreSQL 扩展，也不进入公开 API。公开 UUID 由独立 `PublicIdGenerator` 生成。未来可替换生成器而不改变领域/API。

双索引：`orders(customer_ref, created_at DESC, internal_id DESC)` 支持客户时间线，`orders(public_id)` 唯一索引支持 API 定位；`order_items(order_internal_id)` 支持内部关联，`order_items(product_ref)` 支持审计；`outbox_events(aggregate_internal_id, occurred_at)` 支持聚合顺序，并保留未发布部分索引供未来 publisher 使用。冗余字段只用于查询/历史事实，不作为跨上下文同步副本；订单写入后快照不可变。

## 8. Transactional Outbox 与未来 Kafka

- MVP 仅要求订单变更与版本化事件在同一 PostgreSQL 事务写入 Transactional Outbox；不实现 Kafka publisher、重试或 broker 集成。
- 下一增量的 Topic 建议 `eshop.order.events.v1`，key=`orderId` 保障同订单内有序；届时单独审批运行语义。
- 事件 envelope：`eventId,eventType,eventVersion,aggregateId,occurredAt,correlationId,payload`。
- 事件：`OrderCreated.v1`、`OrderConfirmed.v1`、`OrderCancelled.v1`。
- Outbox 写入保证“事件意图不丢失”，不代表本 MVP 已投递。未来 publisher 采用至少一次投递，消费者按 `eventId` 幂等。
- 本地 Compose 的 Kafka 仅可选 profile；MVP CI 与验收不依赖 broker。

## 9. Redis 与 Elasticsearch 决策

### Redis：MVP 延后业务使用

幂等性需要强一致和可审计，先放 PostgreSQL，不将 Redis 作为事实来源。订单详情初期无需缓存；等性能数据证明有必要后，以 cache-aside + 短 TTL + 状态变化失效策略接入。Docker profile 可预留 Redis 服务用于后续实验。

### Elasticsearch：MVP 延后

客户订单分页由 PostgreSQL 索引满足。仅当需要后台跨字段全文检索/分析时，通过 Kafka 事件建立异步读模型；搜索结果允许最终一致，绝不作为订单事实来源。首期 Compose 可用可选 profile，默认不消耗资源。

## 10. 模块化单体到微服务

建议后端模块：

```text
backend/
  bootstrap/
  order-domain/
  order-application/
  order-adapters-web/
  order-adapters-persistence/
  order-adapters-messaging/
```

如果多模块构建成本过高，可在单一 Gradle/Maven module 内使用同名 package，并用 ArchUnit 强制依赖方向。拆分触发条件：独立扩缩容/发布、组织所有权、可靠性隔离或显著负载差异，而非仅因技术偏好。

拆分步骤：冻结契约 -> 独立数据库 ownership -> 通过事件/API 替换模块调用 -> 抽取部署单元 -> consumer contract 测试。禁止跨服务共享数据库表和共享领域实体 jar。

### 可执行边界规则 / Enforceable boundary rules

- `order-domain` only imports JDK types and its own model; no Spring/JPA/Kafka/HTTP classes.
- `order-application` depends on domain and declares inbound/outbound ports; adapters depend inward.
- Transactions are started by application orchestration; domain behavior is persistence-agnostic.
- Cross-context access occurs only through ports or versioned contracts. ArchUnit verifies dependency direction.
- Extraction readiness requires owned data, stable API/event contracts, consumer contract tests, operational ownership, and an explicit trigger (scale, release cadence, team ownership, or fault isolation).

## 11. 前端设计

- React + TypeScript + Vite（建议）。
- TanStack Router：路由；TanStack Query：server state；TanStack Table：列表（需要时）。
- feature-first：`src/features/orders/{api,components,hooks,pages,schemas}`。
- API client 从 OpenAPI 生成或集中维护；表单边界做运行时 schema 校验。
- Playwright Page/Component Objects 用组合构建测试 fixture，禁止脆弱的 CSS 顺序选择器，优先 role/test-id。

页面：订单创建、客户订单列表、订单详情（含确认/取消）。认证未实现时，由开发 fixture 注入 customer ID，且明确禁止用于生产。

## 12. 仓库与测试资产结构

```text
backend/
frontend/
tests/
  integration/       # 跨进程/API 场景、测试数据与运行器
  uat/               # Gherkin/业务验收脚本与环境配置模板
  performance/       # k6/Gatling 脚本、阈值和报告说明
  e2e/               # Playwright（若不放 frontend/e2e）
  contracts/         # OpenAPI/事件契约测试资产
docker/
docs/adr/
tasks/TASK-0001/
compose.yaml
```

测试目录不得包含真实凭证或生产数据；环境差异用 `.env.example` 和参数化配置。

## 13. Docker

- 后端、前端使用多阶段构建、非 root 用户和 healthcheck。
- Compose 默认启动 app、web、PostgreSQL；Kafka、Redis、Elasticsearch 用 profiles 按需启动。
- 不固定宿主机私密配置；使用环境变量和本地 secrets 文件（gitignored）。
- 生产镜像不可含测试工具；K8s 在后期以独立部署工件引入。

## 14. ADR 建议

1. ADR-001：模块化单体优先，定义拆分触发条件。
2. ADR-002：六边形架构和领域层纯 Java。
3. ADR-003：PostgreSQL + Flyway，禁用自动 schema 更新。
4. ADR-004：MVP Transactional Outbox；Kafka publisher 延后及未来至少一次投递。
5. ADR-005：Redis/Elasticsearch 推迟到有数据证明的场景。
6. ADR-006：API 幂等、乐观锁和 Problem Details。

## 15. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 客户端提交价格可被篡改 | MVP 限测试用途；接入 Catalog 时服务端获取可信快照，公开部署前必须修复。 |
| 无认证造成越权 | 仅开发环境；上线前 Identity/Auth 是阻断项。 |
| Outbox 尚未发布 | 明确 MVP 只保证同事务落库；Kafka publisher 作为下一增量，不宣称事件已送达。 |
| 内部 ID 冲突/时钟回拨 | 唯一 node id、数据库 PK 兜底、时钟回拨 fail-closed、生成器并发测试。 |
| 冗余快照与源系统不一致 | 订单快照代表成交历史并保持不可变；未来可信端口只决定创建瞬间数据，不回写历史。 |
| 过早引入 Redis/ES 增加复杂度 | 首期延后，保留适配器边界和可选 Compose profile。 |
| 状态模型未来不适配支付履约 | 上下文隔离，不把外部状态混入 Order；新规则走策略组合。 |
| E2E 不稳定 | Testcontainers、确定性 fixture、稳定 selector、等待业务状态而非 sleep。 |

## 16. 已建立的技术工作项 / Existing technical work items

仓库已有的 GitHub Issue #1 属于商品目录，不属于本订单模块，以下建议不复用或预设 GitHub 编号；创建时由 GitHub 分配实际编号。共同标签建议：`epic`, `design`, `backend`, `frontend`, `database`, `kafka`, `test`, `devops`, `blocked`, `tdd`。若仓库尚无标签，提交 issue 前先创建。

### 建议 A — `[EPIC] Order module MVP`

**Labels:** `epic`, `blocked`  
**Dependencies:** 无；须先批准 TASK-0001 设计。

**Body:**
实现电子商城订单模块 MVP，覆盖创建、查询、确认、取消、事件与自动化测试。以 TASK-0001 为设计基线。完成条件：子 issue 全部完成、测试门禁通过、文档更新。当前因 API/schema/边界等待批准而 blocked。

### 建议 B — `[DESIGN] Approve order contracts and ADRs`

**Labels:** `design`  
**Dependencies:** 无。

**Body:**
Review TASK-0001 的 REST API、数据库 schema、状态机、Outbox、Redis/ES 延后策略和服务边界。记录决议及修改，批准后将任务状态置为 APPROVED。

### 建议 C — `[BACKEND] Bootstrap order domain and application layer`

**Labels:** `backend`, `tdd`, `blocked`
**Dependencies:** 建议 B。

**Body:**
以测试先行实现纯 Java Order 聚合、值对象、状态机、用例端口与领域错误。不得依赖 Spring/JPA；使用组合而非继承。验收：不变量和转换测试覆盖全部分支。

### 建议 D — `[DATABASE] Add PostgreSQL persistence and Flyway migrations`

**Labels:** `backend`, `database`, `tdd`, `blocked`  
**Dependencies:** 建议 B、C。

**Body:**
实现批准后的表结构、repository adapter、幂等记录和乐观锁。Testcontainers 运行 Flyway 与 repository 集成测试；应用只 validate schema。

### 建议 E — `[BACKEND] Implement versioned REST API`

**Labels:** `backend`, `tdd`, `blocked`  
**Dependencies:** 建议 B、C、D。

**Body:**
实现创建/查询/列表/确认/取消 API、Problem Details、Idempotency-Key、If-Match 和 OpenAPI。验收覆盖成功、校验、404、409 与幂等重放。

### 建议 F — `[OUTBOX] Persist order events transactionally`

**Labels:** `backend`, `tdd`, `blocked`

**Dependencies:** 建议 B、D。

**Body:**
在同一 PostgreSQL 事务写入版本化 Outbox envelope，并以 Testcontainers 验证原子性、唯一性和快照。Kafka publisher、broker、重试与投递语义延后到下一增量。

### 建议 G — `[FRONTEND] Build React order workflows`

**Labels:** `frontend`, `tdd`, `blocked`  
**Dependencies:** 建议 E。

**Body:**
使用 React/TypeScript、TanStack Router/Query 实现创建、列表、详情、确认和取消。加入组件测试、错误/加载/空状态和可访问性基线。

### 建议 H — `[TEST] Add integration, contract and Playwright suites`

**Labels:** `test`, `tdd`, `blocked`  
**Dependencies:** 建议 E、F、G。

**Body:**
建立 `tests/integration`、`tests/contracts`、`tests/e2e`，覆盖 API、事件契约、并发和核心 UI journey。测试应可通过统一命令在容器依赖上运行。

### 建议 I — `[TEST] Add UAT and performance test assets`

**Labels:** `test`, `blocked`  
**Dependencies:** 建议 E、G、H。

**Body:**
建立 `tests/uat` 与 `tests/performance`，提供业务场景、参数化环境、无敏感数据 fixture、k6/Gatling 基线与阈值报告说明。

### 建议 J — `[DEVOPS] Add Docker Compose and CI quality gates`

**Labels:** `devops`, `test`, `blocked`  
**Dependencies:** 建议 D、E、G；Kafka profile 与建议 F 协同。

**Body:**
创建多阶段镜像、非 root 运行、healthcheck 和 Compose；默认 PostgreSQL，Kafka/Redis/ES 为可选 profile。CI 按单元 -> 集成/契约 -> E2E/UAT 顺序运行，压力测试按需触发。

> 上述 A–J 已对应 #3–#11；#12 是 #11 的已关闭重复项。其 GitHub 正文以双语版本为准。/ A–J above map to #3–#11; #12 is the closed duplicate of #11. Their bilingual GitHub bodies are authoritative.

## 17. 路线图与依赖 / Roadmap and dependencies

| Sequence | Deliverable | Issues | Depends on | Gate / Owner |
|---:|---|---|---|---|
| 0 | 设计审查与人工批准 / design review and human approval | #2 | 本 PR / this PR | Human approver; state becomes `APPROVED` |
| 1 | 独立业务故事 / independent business stories | #13–#17 | #2 | Product/UAT acceptance |
| 2 | 领域与持久化 / domain and persistence | #4, #5 | #2; #5 also depends on #4 | Developer agent under supervision |
| 3 | API 与事件 / API and events | #6, #7 | #4–#5; #7 depends on #5 | Developer agent under supervision |
| 4 | React 体验 / React experience | #8 | #6 and stories #13–#16 | Developer agent under supervision |
| 5 | 集成、契约、E2E / integration, contract, E2E | #9 | #6–#8 | Tester agent under supervision |
| 6 | UAT 与性能 / UAT and performance | #10 | #9 and stories #13–#17 | Tester agent under supervision |
| 7 | 容器与 CI 门禁 / containers and CI gates | #11 | #5–#10 as applicable | Developer/tester agents; no deployment |

Dependency flow: `#2 -> (#13–#17) -> #4 -> #5 -> (#6,#7) -> #8 -> #9 -> #10`, with `#11` integrating approved deliverables. #12 is a closed duplicate of #11. #1 remains unrelated and preserved.

## 18. 用户已确认的设计决策 / User-confirmed design decisions

1. MVP 临时接受客户端商品/价格快照，仅限受控开发/UAT；可信 Catalog/定价端口是公开上线阻断项。/ Temporarily accept client product/price snapshots for controlled dev/UAT; a trusted Catalog/pricing port blocks public launch.
2. API 使用 UUID/opaque references；大型表使用应用生成的时间有序 BIGINT internal IDs，并保留双字段/双索引。/ Use UUID/opaque API references plus application-generated time-ordered BIGINT internal IDs and dual fields/indexes for large tables.
3. 创建幂等重放返回 `200`；首次创建返回 `201`。/ Return `200` for create replay and `201` for first creation.
4. 语义校验统一 `422`；请求无法解析为 `400`。/ Use `422` for semantic validation and `400` for unparseable requests.
5. 购物车/提交页合并重复 SKU；订单创建/结算 API 拒绝重复 SKU。当前模块只负责后者。/ Merge duplicate SKUs in cart/review UI; reject them at order create/checkout API. This module owns only the latter boundary.
6. MVP 仅实现 Transactional Outbox；Kafka publisher 延后。/ MVP implements Transactional Outbox only; defer the Kafka publisher.
7. 客户、地址、商品名称与单价保存成交快照并保持不可变，以空间换取查询时间和服务解耦。/ Persist immutable customer, address, product-name, and unit-price snapshots to trade storage for query speed and service decoupling.

上述选择已由用户确认，但因其涉及 API、schema 与架构，PR #18 仍需整体明确批准后才能实施。/ These choices are user-confirmed, but PR #18 still requires explicit overall approval before implementation because they affect API, schema, and architecture.

