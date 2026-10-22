# Add integration, contract and Playwright suites

建立集成、契约和浏览器端到端测试基线。

## Acceptance criteria

- 创建 `tests/integration`、`tests/contracts` 和 `tests/e2e`。
- 覆盖订单 API、事件契约、并发冲突和核心 UI journeys。
- Playwright 使用稳定的 role/test-id selector，不使用固定 sleep。
- 测试可通过统一命令在容器依赖上运行。
- 测试数据为确定性的合成数据，不含真实凭证。

## Dependencies

Blocked by REST API, Kafka event publishing and frontend workflows.
