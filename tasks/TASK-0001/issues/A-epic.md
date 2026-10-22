# Order module MVP

实现电子商城订单模块 MVP，覆盖创建、查询、确认、取消、事件发布与自动化测试。

## User Stories

- 客户可用幂等请求创建订单，并由服务端计算总额。
- 客户可分页查看自己的订单列表与详情。
- 客户可确认草稿订单。
- 客户可取消草稿或已确认订单。
- 测试人员可分别运行集成、契约、E2E、UAT 与压力测试资产。

## Design baseline

- [Requirement](https://github.com/exedeehuashan/online-shop/blob/main/tasks/TASK-0001/requirement.md)
- [Design](https://github.com/exedeehuashan/online-shop/blob/main/tasks/TASK-0001/design.md)
- [Test plan](https://github.com/exedeehuashan/online-shop/blob/main/tasks/TASK-0001/test-plan.md)

## Backlog

- [ ] #2 Design approval
- [ ] #4 Domain and application layer
- [ ] #5 PostgreSQL and Flyway
- [ ] #6 REST API
- [ ] #7 Kafka transactional outbox
- [ ] #8 React workflows
- [ ] #9 Integration, contract and Playwright suites
- [ ] #10 UAT and performance assets
- [ ] #11 Docker Compose and CI quality gates

## Completion criteria

- 所有子 Issue 完成。
- 自动化测试与质量门禁通过。
- 文档和 ADR 与最终实现一致。

当前等待 API、数据库 schema 和模块/事件边界批准，因此实施工作保持 blocked。
