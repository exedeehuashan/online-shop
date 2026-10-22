# TASK-0001：实现记录 / Implementation Record

## Design-stage note / 设计阶段说明

- 状态 / Status：`NOT_STARTED / PENDING_APPROVAL`。
- 本 PR 仅修订需求、架构、测试计划、路线图和 GitHub backlog；没有应用代码、迁移、测试脚本、容器或部署变更。/ This PR revises requirements, architecture, test planning, roadmap, and backlog only; it contains no application code, migrations, test scripts, containers, or deployment changes.
- 人类批准 #2 前不得进入 `DEVELOPING`。/ Do not enter `DEVELOPING` until a human approves #2.
- 批准后，由明确指定的 developer/tester agents 在人类监督下工作，并各自在 feature branch 通过 PR 交付；禁止直接提交 `main`、自动合并、部署或生产变更。/ After approval, explicitly assigned developer/tester agents work under human supervision using feature branches and PRs; no direct main commits, auto-merge, deployment, or production changes.

## Approval record / 批准记录

批准时必须记录：批准人/日期、API 与认证选择、数据库 schema/Flyway 决策、模块/事件边界、Outbox-only MVP/Kafka publisher 延后、Redis/Elasticsearch 延后策略，以及已确认设计决策。/ Approval must record approver/date, API and auth choices, database schema/Flyway decision, module/event boundaries, Outbox-only MVP/Kafka publisher deferral, Redis/Elasticsearch deferral, and the confirmed design decisions.

当前决策记录 / Current decision record（2026-10-22）：用户已确认客户端临时价格快照、public/opaque + internal time-ordered 双 ID、重放 `200`、语义错误 `422`、购物车合并而订单 API 拒绝重复 SKU、仅 Transactional Outbox、不可变冗余快照。/ The user confirmed temporary client price snapshots, public/opaque plus internal time-ordered IDs, replay `200`, semantic `422`, cart-side merge with order-API duplicate rejection, Transactional Outbox only, and immutable redundant snapshots.

整体实施批准 / Overall implementation approval：**None / 无**。PR #18 与 Issue #2 仍须明确批准后才能开始开发。/ PR #18 and Issue #2 still require explicit approval before development starts.

