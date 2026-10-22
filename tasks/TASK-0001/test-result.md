# TASK-0001：测试结果 / Test Results

## Issue #4 developer verification / Issue #4 开发者验证

- 日期 / Date：2026-10-22
- 命令 / Command：`mvn -B clean verify`
- 结果 / Result：`PASS` — 35 tests, 0 failures, 0 errors, 0 skipped.
- 覆盖 / Coverage：金额约束与计算、订单创建不变量、重复 SKU、不可变快照、确认/取消状态机与幂等行为、应用端口编排与客户隔离、63-bit 时间有序 ID 与时钟回拨 fail-closed、ArchUnit 领域纯净性和依赖方向。/ Money constraints/calculation, order creation invariants, duplicate SKU rejection, immutable snapshots, confirm/cancel state machine and idempotent behavior, application-port orchestration and customer isolation, 63-bit time-ordered IDs with fail-closed clock rollback, and ArchUnit domain purity/dependency direction.
- 未执行 / Not run：数据库、REST/API、Kafka、浏览器、UAT 与性能测试属于后续 Issues #5–#11。/ Database, REST/API, Kafka, browser, UAT, and performance tests belong to later Issues #5–#11.

## Issue #4 独立 QA 结果 / Independent QA result (PR #19)

- 日期 / Date：2026-10-22
- 分支 / Branch：`feat/issue-4-order-domain-application`
- 基线提交 / Baseline commit：`e4ede4f`
- 结论 / Verdict：**PASS（无合并阻断 / no merge blocker）**，含 3 项 Medium 缺陷，建议在本 PR 或进入 Issue #5 前修复。/ PASS with three Medium defects; fix in this PR or before Issue #5.

### 环境与命令 / Environment and commands

| 项 / Item | 值 / Value |
|---|---|
| JDK | OpenJDK 17.0.20.1 |
| Maven | 3.8.7 |
| 构建 / Build | `mvn -B clean verify` → **BUILD SUCCESS** |
| 测试 / Tests | 31 项，执行 28，跳过 3（缺陷复现用例默认禁用），失败 0 / 31 total, 28 executed, 3 skipped, 0 failures |
| 领域层 / Domain module | 12 tests（`OrderDomainQaTest` 6、`OrderTest` 4、`MoneyTest` 2） |
| 应用层 / Application module | 19 tests（`OrderApplicationQaTest` 7、`IdGeneratorQaTest` 4、`OrderApplicationTest` 2、`TimeOrderedIdGeneratorTest` 2、`ArchitectureTest` 1、`KnownDefectsQaTest` 3 skipped） |
| 缺陷复现 / Defect repro | `mvn -B -pl backend/order-application -am test -Dtest=KnownDefectsQaTest -Djunit.jupiter.conditions.deactivate='*' -Dsurefire.failIfNoSpecifiedTests=false` → 3 failures |
| 空白检查 / Whitespace | `git diff --check` → PASS |

当前回归套件共 35 项测试，全部启用且通过。/ The current regression suite contains 35 tests, all enabled and passing.

### Issue #4 验收矩阵 / Acceptance matrix

| 验收标准 / Criterion | 结果 / Result | 证据 / Evidence |
|---|---|---|
| 领域层不依赖 Spring/JPA/Kafka | PASS | 领域 import 仅 JDK；ArchUnit `ArchitectureTest` |
| 组合而非继承；Money、AddressSnapshot、OrderItem 明确建模 | PASS | 全部 `record`/`final class`，无 `extends`（仅实现端口接口）；构造注入 |
| 不变量与所有状态转换先有失败测试 | PARTIAL | 主要不变量/转换有测试；`DRAFT→CANCELLED` 原先未覆盖（QA 已补）；红灯阶段无 git 证据 |
| Clock、ID generator、repository 通过端口注入 | PASS | 构造器注入 `Clock`、`InternalIdGenerator`、`PublicIdGenerator`、`OrderRepository`、`DomainEventRepository`、`TransactionRunner` |
| ArchUnit 强制依赖方向 | PASS | domain 仅依赖 `java..` 与自身；application 仅依赖 domain/自身/JDK |

### 需求映射（本层可验证部分）/ Requirement mapping (verifiable at this layer)

| 需求 / Requirement | 结果 / Result | 说明 / Note |
|---|---|---|
| FR-01 幂等创建 / idempotent create | NOT IMPLEMENTED | 应用层无幂等键或幂等端口，留待 #5/#6；`CreateOrderCommand` 契约届时需变更 |
| FR-02 校验与总额 / validation and totals | PASS | 空单、重复 SKU、负价、超两位小数、行小计一致 |
| FR-03 客户订单查询 / customer queries | PASS | `Get`/`List` 按客户过滤；分页参数边界已测；排序契约未定义 |
| FR-04 确认 / confirm | PASS | `DRAFT→CONFIRMED`，重复确认幂等且不重复发事件 |
| FR-05 取消 / cancel | PASS | `DRAFT/CONFIRMED→CANCELLED`，`CANCELLED` 幂等 |
| FR-06 非法转换 / illegal transition | PASS | `CANCELLED + Confirm → OrderStateConflictException` |
| FR-07 领域事件 / events | PASS | 版本化事件；真实 Outbox 属 #6 |
| FR-08 乐观锁 / optimistic locking | PARTIAL | 聚合维护 `version`，但端口无冲突信号；冲突检测属 #5 |

### 缺陷清单 / Defect list

| ID | 级别 / Severity | 组件 / Component | 状态 / Status |
|---|---|---|---|
| D1 | Medium (P2) | `TimeOrderedIdGenerator` | Open |
| D2 | Medium (P2) | `CreateOrderHandler` / `Money` | Open |
| D3 | Medium (P2) | `CustomerReference` / `ProductReference` | Open（与既有 Codex 评论一致 / matches existing Codex finding） |

#### D1 — ID 生成器在 epoch + node 0 返回 0 / generator returns 0 at epoch with node 0

- 位置 / Location：`backend/order-application/.../TimeOrderedIdGenerator.java:36`
- 期望 / Expected：`nextId()` 始终为正 63-bit 值，可被 `OrderInternalId` 接受 / always a positive 63-bit value accepted by `OrderInternalId`.
- 实际 / Actual：`new TimeOrderedIdGenerator(0, Clock.fixed(2024-01-01T00:00:00Z))` 首次调用返回 `0`；`new OrderInternalId(0)` 抛 `DomainValidationException`。/ First call returns `0`; `OrderInternalId` rejects it.
- 复现 / Reproduce：运行上述复现命令，`d1_firstIdAtEpochMustBeAPositive63BitValue` 失败：`Expecting actual: 0L to be greater than: 0L`.
- 影响 / Impact：使用确定性时钟（fix clock at ID epoch）的创建流程会在生成 ID 后立即失败；与 PR 声称的 “positive 63-bit” 契约不一致。/ Deterministic-clock flows fail immediately; contradicts the stated positive-63-bit contract.
- 建议 / Suggested fix：生成后校验 `id > 0`，或将 epoch/sequence 起点偏移，并补充该边界测试。/ Validate `id > 0` or offset the epoch/sequence origin, plus a boundary test.

#### D2 — 空单价抛 NPE 而非语义校验错误 / null unit price throws NPE instead of a semantic validation error

- 位置 / Location：`CreateOrderHandler.java:39` → `Money.java:9`
- 期望 / Expected：`DomainValidationException(INVALID_VALUE)`，REST 层映射 `422`（US-01）；空列表元素同理。/ Domain error mapping to 422.
- 实际 / Actual：`NullPointerException: amount`（通用 `500`）。`items` 含 `null` 元素同样 NPE。/ NPE, generic 500; null item element also NPEs.
- 复现 / Reproduce：`d2_nullUnitPriceMustBeASemanticValidationError` 失败，`but was: java.lang.NullPointerException: amount`。
- 影响 / Impact：语义非法的请求被当作服务端故障，违反 US-01 的 `422` 契约，且会污染 5xx 告警。/ Invalid requests look like server faults and pollute 5xx alerts.
- 建议 / Suggested fix：在应用层校验 `unitPrice`/item 非空并转换为领域校验错误，或让 `Money.nzd` 显式校验 null。/ Validate in the handler or in `Money.nzd`.

#### D3 — 不透明引用被 trim 导致标识碰撞 / opaque references are trimmed and collide

- 位置 / Location：`CustomerReference.java:4`、`ProductReference.java:4`（共用 `Validation.required`）
- 期望 / Expected：不透明引用原样保留；`" customer-1 "` 与 `"customer-1"` 不相等（design §3：不透明、不假设可解析）。/ Preserve raw value; distinct identifiers must not collide.
- 实际 / Actual：两者均归一化为 `customer-1`，`equals` 为 true。/ Both normalize to `customer-1`.
- 复现 / Reproduce：`d3_opaqueReferencesMustPreserveTheirRawValue` 失败：`CustomerReference[value=customer-1] not to be equal to CustomerReference[value=customer-1]`。
- 影响 / Impact：Get/Confirm/Cancel 的所有权校验与未来幂等键可能把不同主体合并，属身份边界风险。/ Ownership checks and future idempotency keys can merge distinct principals.
- 建议 / Suggested fix：新增不 trim 的 `requiredOpaque` 校验；人类可读快照（姓名/地址/商品名）继续 trim。/ Add a non-trimming opaque validator; keep trimming for human-readable snapshots.

### 非阻断观察 / Non-blocking observations

| ID | 观察 / Observation | 建议 / Recommendation |
|---|---|---|
| O1 | `Order` 无重建工厂；从数据库加载会触发 `OrderCreated` 且无法恢复 status/version / no reconstitution factory | #5 增加 `reconstitute(...)` 并禁止加载时发事件 |
| O2 | `OrderRepository.save(Order)` 无期望版本参数，应用层无法区分版本冲突 / no expected-version signal | #5 定义 `OptimisticLockException` 或 `save(order, expectedVersion)` |
| O3 | 便捷构造器默认 `TransactionRunner.direct()`，易误用为非事务写入 / default non-transactional runner | #6 移除默认值或改为显式工厂 |
| O4 | `TransactionRunner` 标注 `@FunctionalInterface`，但泛型抽象方法无法用 lambda 实现 / invalid lambda target | 移除注解或改为 `Supplier<T>` 具体签名 |
| O5 | ID 生成器无节点分配/冲突检测，PR 描述 “node allocation” 略超实际 / no node allocator | 在部署文档或 #6 中补齐 |
| O6 | `AddressSnapshot` 接受非字母两位国家码，且未用 `Locale.ROOT` 大写 / country code not validated as letters | 增加 `[A-Z]{2}` 校验与 `Locale.ROOT` |
| O7 | TDD 红灯阶段未体现在 git 历史（单个 feat 提交），PR 说明红灯停在依赖引导 / red phase not evidenced | 后续按 “失败测试提交 → 实现提交” 拆分 |
| O8 | `CreateOrderHandler` 在验证前消耗内部 ID / IDs consumed before validation | 校验通过后再分配 |
| O9 | Handler 返回可变聚合实例，内存适配器会别名共享 / returns live aggregate | 适配器层不得跨请求泄漏实例 |

### 结论 / Conclusion

- 领域模型、状态机、端口注入与模块依赖符合 Issue #4 的架构意图；`mvn clean verify` 通过。/ Domain model, state machine, port injection, and module boundaries meet Issue #4 intent; build is green.
- 未发现 Critical/High 缺陷，**不阻断合并**；D1–D3 为 Medium，建议修复后再进入 #5/#6。/ No Critical/High defects; D1–D3 are Medium and should be fixed before #5/#6.
- 未合并 PR，未修改生产代码，未触达 Issue #5–#11 范围。/ PR not merged; no production code changed; Issues #5–#11 untouched.

## D1–D3 修复验证 / D1–D3 remediation verification

- 日期 / Date：2026-10-22
- 状态流转 / Status transition：`FAILED → DEVELOPING`
- 命令 / Command：`mvn -B clean verify`
- 结果 / Result：**BUILD SUCCESS** — 35 tests, 0 failures, 0 errors, 0 skipped（domain 14；application 21）。
- 回归 / Regression：`KnownDefectsQaTest` 的 3 个缺陷复现已取消 `@Disabled` 并成为常规构建的一部分；另增加 2 个 ID 边界测试。/ All three defect reproductions in `KnownDefectsQaTest` are enabled as normal build regressions; two additional ID boundary tests were added.
- 空白检查 / Whitespace：`git diff --check` → **PASS**。

| ID | 修复结果 / Resolution | 状态 / Status |
|---|---|---|
| D1 | epoch/node 0 首个 ID 为 `1`，后续不重复；最大支持时间仍在正 63-bit 范围，越界 fail-closed。/ First ID is `1` with no duplicate; maximum supported time stays positive 63-bit and overflow fails closed. | Fixed |
| D2 | null item/null unit price 抛 `DomainValidationException(INVALID_VALUE)`，不再泄漏 NPE。/ Null item/unit price raises the domain validation error rather than leaking NPE. | Fixed |
| D3 | opaque reference 对空白和首尾空格显式拒绝，合法值原样保留。/ Blank or surrounding whitespace is explicitly rejected and valid opaque values are preserved. | Fixed |

R1 回归补充：边界检测通过集中 code-point predicate 覆盖完整 Unicode `White_Space` 集合（包括 U+0085 NEL、U+2003 EM SPACE 与 U+00A0 NBSP），不使用 trim 或规范化；`CustomerReference` 与 `ProductReference` 均覆盖 U+0085 首尾边界，内部 U+0085 保持精确不变。/ R1 regression coverage: a central code-point predicate handles the complete Unicode `White_Space` set (including U+0085 NEL, U+2003 EM SPACE, and U+00A0 NBSP) without trimming or normalisation; both reference types cover U+0085 at either boundary and preserve an internal U+0085 exactly.

修复严格限于 Issue #4；未触碰 Issues #5–#11，PR #19 未合并。/ Remediation is strictly scoped to Issue #4; Issues #5–#11 are untouched and PR #19 remains unmerged.
