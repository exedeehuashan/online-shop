# TASK-0001：订单模块设计草案

> 状态：DESIGNED，等待批准。本文是实现输入，不代表已承诺的公开契约。

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
                            Kafka
```

依赖方向：`interfaces -> application -> domain`；`infrastructure` 实现 domain/application 定义的端口。使用组合对象（Money、Address、OrderItem）而非继承式基础实体。

## 2. 限界上下文与边界

### Order（本次实现）

- 拥有订单、订单项、地址快照、金额、状态转换和领域事件。
- 不直接读取其他模块的表。

### Catalog（外部/未来）

- 拥有 SKU、商品名称和当前售价。
- MVP 由调用方提交商品快照；下一阶段以 `ProductSnapshotProvider` 端口替换，订单依旧保存快照。

### Inventory / Payment / Fulfillment / Identity（未来）

- 仅通过显式命令/API 和版本化事件协作。
- Order 不继承或共享这些上下文的实体。

## 3. 领域模型

### 聚合根 `Order`

- `OrderId`（UUID）
- `CustomerId`（字符串/UUID value object；待 Identity 决定）
- `status: OrderStatus`
- `items: List<OrderItem>`
- `shippingAddress: AddressSnapshot`
- `total: Money`
- `cancellationReason?`
- `createdAt`, `updatedAt`
- `version`（乐观锁）

### 值对象/实体

- `OrderItem`：`orderItemId`, `productId`, `sku`, `productName`, `unitPrice: Money`, `quantity`, `lineTotal`。
- `Money`：`amount + currency`，封装加法/乘法与舍入规则。
- `AddressSnapshot`：收件人、地址行、城市、地区、邮编、国家代码、电话（敏感字段日志脱敏）。
- 应用服务组合 `Clock`、`IdGenerator`、`OrderRepository`、`OutboxRepository` 等接口，便于确定性测试。

### 不变量

- 订单至少一项；SKU 在单一订单内是否允许重复：建议拒绝重复并返回校验错误。
- 所有订单项币种一致且为 NZD；总额等于行小计之和。
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
| POST | `/api/v1/customers/{customerId}/orders` | 创建；必需 `Idempotency-Key` | `201` + Location；幂等重放可 `200` |
| GET | `/api/v1/customers/{customerId}/orders/{orderId}` | 获取详情 | `200` |
| GET | `/api/v1/customers/{customerId}/orders?page=0&size=20` | 分页列表 | `200` |
| POST | `.../{orderId}/confirmation` | 确认 | `200` |
| POST | `.../{orderId}/cancellation` | 取消，body 含 reason | `200` |

创建请求示意：

```json
{
  "items": [
    {"productId": "uuid", "sku": "PHONE-001", "name": "Phone", "unitPrice": {"amount": "999.00", "currency": "NZD"}, "quantity": 1}
  ],
  "shippingAddress": {
    "recipient": "Example", "line1": "1 Example St", "city": "Auckland",
    "region": "Auckland", "postalCode": "1010", "countryCode": "NZ"
  }
}
```

响应包括 `id, customerId, status, items, total, shippingAddress, version, createdAt, updatedAt`。并发命令建议使用 `If-Match: "<version>"`。

错误使用 `application/problem+json`，字段至少含 `type,title,status,detail,instance,code,violations,correlationId`。建议映射：校验 `400/422`、未找到 `404`、状态/版本/幂等冲突 `409`。

## 7. PostgreSQL / Flyway schema 草案（待批准）

```text
orders
  id uuid PK
  customer_id varchar(64) NOT NULL
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
  id uuid PK
  order_id uuid NOT NULL FK orders(id)
  product_id uuid NOT NULL
  sku varchar(100) NOT NULL
  product_name varchar(300) NOT NULL
  unit_amount numeric(19,2) NOT NULL
  currency char(3) NOT NULL
  quantity integer NOT NULL CHECK (quantity > 0)
  line_total numeric(19,2) NOT NULL
  UNIQUE(order_id, sku)

order_idempotency
  customer_id varchar(64) NOT NULL
  idempotency_key varchar(128) NOT NULL
  request_hash char(64) NOT NULL
  order_id uuid NOT NULL
  created_at timestamptz NOT NULL
  PRIMARY KEY(customer_id, idempotency_key)

outbox_events
  id uuid PK
  aggregate_type varchar(100) NOT NULL
  aggregate_id uuid NOT NULL
  event_type varchar(150) NOT NULL
  event_version integer NOT NULL
  payload jsonb NOT NULL
  occurred_at timestamptz NOT NULL
  published_at timestamptz NULL
  attempts integer NOT NULL DEFAULT 0
```

索引：`orders(customer_id, created_at DESC, id DESC)`、`order_items(order_id)`、未发布 outbox 的部分索引。Flyway migrations 位于后端资源目录；测试用 Testcontainers 运行真实迁移。JPA schema generation 设为 validate。

## 8. Kafka 与事件

- 使用 Transactional Outbox 避免数据库提交与 Kafka 发布的双写不一致。
- Topic 建议 `eshop.order.events.v1`，key=`orderId` 保障同订单内有序。
- 事件 envelope：`eventId,eventType,eventVersion,aggregateId,occurredAt,correlationId,payload`。
- 事件：`OrderCreated.v1`、`OrderConfirmed.v1`、`OrderCancelled.v1`。
- 投递语义为至少一次；消费者必须按 `eventId` 幂等。Schema 演进遵循向后兼容，首期 JSON；后续评估 Schema Registry。
- 本地 Docker 可启动 Kafka；应用允许 profile 禁用 publisher，核心测试不依赖 broker。

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
- Compose 默认启动 app、web、PostgreSQL、Kafka；Redis/Elasticsearch 用 profiles 按需启动。
- 不固定宿主机私密配置；使用环境变量和本地 secrets 文件（gitignored）。
- 生产镜像不可含测试工具；K8s 在后期以独立部署工件引入。

## 14. ADR 建议

1. ADR-001：模块化单体优先，定义拆分触发条件。
2. ADR-002：六边形架构和领域层纯 Java。
3. ADR-003：PostgreSQL + Flyway，禁用自动 schema 更新。
4. ADR-004：Transactional Outbox 与 Kafka 至少一次投递。
5. ADR-005：Redis/Elasticsearch 推迟到有数据证明的场景。
6. ADR-006：API 幂等、乐观锁和 Problem Details。

## 15. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 客户端提交价格可被篡改 | MVP 限测试用途；接入 Catalog 时服务端获取可信快照，公开部署前必须修复。 |
| 无认证造成越权 | 仅开发环境；上线前 Identity/Auth 是阻断项。 |
| Kafka 重复/乱序 | aggregate key、有版本事件、消费端幂等。 |
| Outbox 积压 | 指标、重试/退避、死信/人工重放流程。 |
| 过早引入 Redis/ES 增加复杂度 | 首期延后，保留适配器边界和可选 Compose profile。 |
| 状态模型未来不适配支付履约 | 上下文隔离，不把外部状态混入 Order；新规则走策略组合。 |
| E2E 不稳定 | Testcontainers、确定性 fixture、稳定 selector、等待业务状态而非 sleep。 |

## 16. 建议 GitHub Issues

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

### 建议 F — `[KAFKA] Publish order events via transactional outbox`

**Labels:** `backend`, `kafka`, `tdd`, `blocked`  
**Dependencies:** 建议 B、D。

**Body:**
在同事务写 Outbox，并以后台 publisher 发布版本化事件到 Kafka。增加重试、指标和 Testcontainers 集成测试；验证重复投递假设。

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
创建多阶段镜像、非 root 运行、healthcheck 和 Compose；默认 PostgreSQL/Kafka，Redis/ES 为可选 profile。CI 按单元 -> 集成/契约 -> E2E/UAT 顺序运行，压力测试按需触发。

