# Publish order events via transactional outbox

通过 Transactional Outbox 可靠发布版本化订单事件。

## Acceptance criteria

- 支持 `OrderCreated.v1`、`OrderConfirmed.v1`、`OrderCancelled.v1`。
- Topic key 使用 order ID，定义版本化事件 envelope。
- Publisher 支持失败重试、指标和可观测的积压。
- Testcontainers 验证提交、发布、失败恢复和至少一次投递语义。
- 消费者幂等要求写入契约文档。

## Dependencies

Blocked by design approval and persistence.
