# Add UAT and performance test assets

建立独立 UAT 与性能测试目录和可执行基线。

## Acceptance criteria

- 创建 `tests/uat` 与 `tests/performance`，各含 README 和统一命令。
- UAT 将 User Story 验收标准映射为 Given/When/Then 场景。
- k6（或批准后的 Gatling）覆盖读写混合、幂等重放、热点客户和并发冲突。
- 报告读取/写入 P95、错误率、资源与 Outbox/Kafka 指标。
- 环境配置参数化，不提交秘密或生产数据。

## Dependencies

Blocked by REST API, frontend and automated test baseline.
