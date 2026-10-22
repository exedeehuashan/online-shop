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
