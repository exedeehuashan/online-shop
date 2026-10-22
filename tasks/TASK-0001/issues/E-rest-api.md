# Implement versioned order REST API

实现订单创建、查询、列表、确认和取消 API。

## Acceptance criteria

- API 采用 `/api/v1` 和批准后的路径。
- 创建支持 `Idempotency-Key`，并区分重放和键冲突。
- 状态命令支持批准后的乐观锁语义。
- 错误使用 `application/problem+json`。
- OpenAPI 记录成功、校验、404 和 409 契约。
- API 测试覆盖客户隔离、幂等与并发冲突。

## Dependencies

Blocked by design approval, domain/application layer and persistence.
