## Summary / 摘要

Closes #4.

Bootstrap the approved Order domain and application boundary. / 建立已批准的订单领域与应用层边界。

## Changes / 变更

- Add Java 17 Maven modules for `order-domain` and `order-application`. / 添加 Java 17 Maven 的领域与应用模块。
- Implement the pure-Java `Order` aggregate, value objects, invariants, state transitions, and versioned domain events. / 实现纯 Java 的 `Order` 聚合、值对象、不变量、状态转换及版本化领域事件。
- Add create/get/list/confirm/cancel inbound use cases and repository, event, transaction, Clock, UUID, and internal-ID composition points. / 添加创建、获取、列表、确认、取消用例，以及仓储、事件、事务、时钟、UUID 和内部 ID 的组合端口。
- Add a positive 63-bit time-ordered ID generator with node allocation, sequence protection, and fail-closed clock rollback. / 添加正数 63-bit 时间有序 ID 生成器，包含节点分配、序列保护和时钟回拨 fail-closed。
- Enforce pure-domain and inward application dependencies with ArchUnit. / 使用 ArchUnit 强制纯领域层和向内依赖。
- Record implementation and developer verification evidence in TASK-0001. / 在 TASK-0001 中记录实施和开发者验证证据。

## TDD and verification / TDD 与验证

- Red: behavior tests were introduced before production types and failed on the missing model/application API. / 红灯：先添加行为测试，因领域和应用类型尚不存在而失败。
- Green/refactor: added the minimum implementation, then introduced transaction composition and explicit event versions. / 绿灯与重构：添加最小实现，再补充事务组合与显式事件版本。
- `mvn verify` — PASS, 11 tests, 0 failures/errors/skips. / 通过，11 项测试，无失败、错误或跳过。
- `git diff --check` — PASS. / 通过。
- TASK-0001 status JSON validation — PASS. / TASK-0001 状态 JSON 验证通过。

## Scope boundary / 范围边界

This PR does not implement persistence/Flyway, REST/Spring, Kafka publishing, frontend, Docker, CI, UAT, or performance work assigned to Issues #5–#11. / 本 PR 不实现分配给 Issues #5–#11 的持久化/Flyway、REST/Spring、Kafka 发布、前端、Docker、CI、UAT 或性能工作。

Human review is required. Do not auto-merge. / 需要人工审核，请勿自动合并。
