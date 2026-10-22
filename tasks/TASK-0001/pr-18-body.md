## Summary / 摘要

Design-only revision for TASK-0001. It reconciles the Order scope with Catalog issue #1, records the user-confirmed API/schema/event decisions, and keeps implementation blocked until explicit overall approval. / TASK-0001 仅设计修订：协调订单范围与 Catalog Issue #1，记录用户确认的 API/schema/事件决策，并在整体明确批准前继续阻止实施。

## Confirmed decisions / 已确认决策

1. Temporarily accept client-supplied price snapshots for controlled dev/UAT; trusted Catalog/pricing remains a public-launch gate. / 临时接受客户端价格快照，仅限受控开发/UAT；可信 Catalog/定价仍是公开上线门禁。
2. Use UUID/opaque public references plus application-generated 63-bit time-ordered internal IDs and dual indexes; no PostgreSQL extension dependency. / 使用 UUID/opaque 对外引用、应用生成的 63-bit 时间有序内部 ID 与双索引；不依赖 PostgreSQL 扩展。
3. First create returns `201`; identical idempotent replay returns `200`. / 首次创建返回 `201`；相同幂等重放返回 `200`。
4. Semantic validation returns `422`; unparseable requests return `400`. / 语义校验返回 `422`；无法解析请求返回 `400`。
5. Cart/review UI merges duplicate SKU quantities; order create/checkout API rejects duplicate SKU. / 购物车/提交页合并重复 SKU 数量；订单创建/结算 API 拒绝重复 SKU。
6. MVP implements Transactional Outbox persistence only; Kafka publisher is deferred. / MVP 仅实现 Transactional Outbox 持久化；Kafka publisher 延后。
7. Customer, address, product-name, and unit-price snapshots are immutable historical facts. / 客户、地址、商品名称与单价快照是不可变历史事实。

## Approval gate / 批准门禁

- Approval issue / 批准 Issue: #2
- Epic / 史诗: #3
- Stories / 故事: #13–#17
- Implementation/testing / 实施与测试: #4–#11
- #12 remains a closed duplicate of #11. / #12 保持为 #11 的已关闭重复项。
- #1 remains independent Catalog scope; only its presentation is made bilingual. / #1 保持独立 Catalog 范围；仅将呈现改为双语。

## Scope / 范围

- Documentation and GitHub backlog only / 仅文档与 GitHub backlog
- State remains `DESIGNED / PENDING_APPROVAL` / 状态保持 `DESIGNED / PENDING_APPROVAL`
- No application code, migration, test execution, container, merge, deployment, or production change / 无应用代码、迁移、测试执行、容器、合并、部署或生产变更
- Future developer/tester-agent work requires explicit human supervision / 后续 developer/tester-agent 工作需要人类明确监督

## Verification / 验证

- `git diff --check` passes / 通过
- `status.json` parses with `jq` / JSON 可解析
- Issues #1–#17 are bilingual; #2–#17 show Priority, Sequence, Dependencies, Status, and Acceptance Criteria / Issue #1–#17 均为双语；#2–#17 显示优先级、顺序、依赖、状态与验收标准

Closes no issues. Explicit overall approval on #2/PR #18 is still required before implementation. / 本 PR 不关闭 Issue；实施前仍需在 #2/PR #18 明确整体批准。
