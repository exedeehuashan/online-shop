## Summary / 摘要

Implements Issue #6's approved versioned Order REST API and OpenAPI over the existing application and PostgreSQL adapters. / 基于现有应用层与 PostgreSQL 适配器，实现 Issue #6 已批准的版本化订单 REST API 与 OpenAPI。

Closes #6

## Delivered / 已交付

- Create/list/detail/confirm/cancel under `/api/v1/customers/{customerId}/orders`. / 创建、列表、详情、确认、取消端点。
- First create `201 + Location`; identical replay `200` with the original public order UUID; different payload `409`. / 首次创建 201，相同重放 200 并返回原 public UUID，异载荷 409。
- RFC 9457 Problem Details: parse/binding `400`, semantic/duplicate-SKU `422`, missing/unauthorized `404`, idempotency/state/version conflicts `409`. / RFC 9457 错误映射覆盖 400/422/404/409。
- `If-Match` optimistic version checks and transactional rollback of losing idempotency candidates. / `If-Match` 乐观锁与幂等竞争失败候选事务回滚。
- Responses expose no internal BIGINT identifiers. OpenAPI states customer IDs and client prices are dev/controlled-UAT only. / 响应不暴露内部 BIGINT；OpenAPI 明示 customer ID 与客户端价格仅限开发/受控 UAT。

## TDD and verification / TDD 与验证

- Red contract commit: `6096e87`. / 红灯契约提交：`6096e87`。
- Focused PostgreSQL API suite: 4/4 passed, 0 skipped. / PostgreSQL API 定向测试 4/4 通过。
- Full `mvn -B clean verify`: 49/49 passed, 0 skipped/failures/errors against PostgreSQL 16.15. / 全量验证 49/49 通过。
- `git diff --check`: PASS.

## Scope and risks / 范围与风险

No Issues #7–#11, authentication, trusted Catalog pricing, Kafka publisher, frontend, deployment, compose file, or secrets are included. The API must remain restricted to development/controlled UAT until authentication and trusted pricing are integrated. / 未包含 Issues #7–#11、认证、可信 Catalog 定价、Kafka 发布、前端、部署、compose 或秘密；接入认证与可信定价前仅可用于开发/受控 UAT。

Do not auto-merge; independent tester QA is required. / 禁止自动合并，需独立 tester QA。
