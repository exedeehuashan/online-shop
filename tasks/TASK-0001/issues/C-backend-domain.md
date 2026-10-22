# Bootstrap order domain and application layer

以 TDD 实现纯 Java 的 Order 聚合、值对象、状态机、应用用例端口与领域错误。

## Acceptance criteria

- 领域层不依赖 Spring、JPA 或 Kafka。
- 使用组合而非继承；Money、AddressSnapshot、OrderItem 明确建模。
- 订单不变量及所有状态转换先有失败测试，再有最小实现。
- Clock、ID generator、repository 等通过端口注入。
- ArchUnit 强制依赖方向。

## Dependency

Blocked by design approval.
