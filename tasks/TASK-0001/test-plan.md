# TASK-0001：测试计划

> 本计划描述未来实现的测试策略；当前尚未执行测试。

## 原则

- 严格 TDD：先写失败测试，再写最小实现，最后重构。
- 测试行为和契约，避免绑定内部实现。
- 优先确定性测试：注入 Clock/ID，禁止固定 sleep。
- 真实 PostgreSQL/Kafka 行为使用 Testcontainers，避免以内存替身掩盖差异。

## 测试金字塔

1. **领域单元测试（最多）**：Money、OrderItem、Order 不变量、状态机、幂等行为。
2. **应用组件测试**：用 fake ports 验证 handler 编排、事务结果和错误映射。
3. **适配器集成测试**：Flyway、repository、乐观锁、唯一键、Outbox、Kafka 发布。
4. **API/契约测试**：OpenAPI、Problem Details、headers、JSON schema、事件兼容性。
5. **前端组件测试**：表单、状态、可访问性、TanStack Query 行为。
6. **Playwright E2E（少量）**：创建 -> 查看 -> 确认；创建 -> 取消；失败恢复。
7. **UAT**：按 User Story 的 Given/When/Then，在类生产 Compose 环境执行。
8. **压力测试**：读写混合、幂等重放、热点客户、并发状态冲突和稳定性。

## 核心测试矩阵

| 场景 | 层级 | 关键断言 |
|---|---|---|
| 创建合法订单 | unit/API/integration/E2E | 总额、DRAFT、201、唯一数据/事件 |
| 空项/负价格/零数量 | unit/API | 拒绝且不持久化 |
| 幂等重放 | integration/API/perf | 单订单、单事件、稳定响应 |
| 相同键不同载荷 | integration/API | 409 |
| 确认/取消状态机 | unit/API | 合法转换、终态幂等、非法 409 |
| 并发确认和取消 | integration/perf | 一个胜出，另一个 409，无丢失更新 |
| Outbox 发布失败/重试 | integration | DB 已提交、最终发布、可重复但不丢失 |
| 客户隔离 | API/security | 非所有者得到 404 |
| 分页稳定性 | integration/API | 排序稳定、边界正确 |
| UI 主流程 | Playwright | 可访问交互、无脆弱等待 |

## 测试数据

- Builders/Object Mothers 生成订单；明确边界案例，不共享可变 fixture。
- 固定 clock 与 UUID provider，事件快照可重复。
- UAT/压力环境只使用合成客户、地址和商品。

## 压力测试草案

- 工具建议 k6（易容器化、JS 脚本）；如团队偏 Java 可选 Gatling。
- 工作负载：70% 列表/详情、20% 创建、5% 确认、5% 取消。
- 阶段：smoke -> baseline -> load -> spike -> soak。
- 初始目标：错误率 < 1%；读取 P95 < 300ms；写入 P95 < 500ms；无数据不变量破坏。目标需在确定硬件/并发后批准。
- 单独观测数据库连接、Outbox lag、Kafka publish latency、JVM/GC。

## 执行门禁

- PR：单元、组件、静态检查、架构规则。
- 合并前：PostgreSQL/Kafka 集成与契约测试。
- 候选版本：Playwright E2E 和 UAT。
- 按需/夜间：压力与 soak。

## 退出标准

- 所有验收标准映射到自动化测试，关键路径全绿。
- 无 Critical/High 缺陷；Medium 有明确处置。
- Flyway 从空库迁移成功且 schema validate 通过。
- API/事件契约通过，主要错误分支已测试。
- 性能基线被记录，未出现数据损坏或资源持续泄漏。

