# Order module MVP

实现电子商城订单模块 MVP，覆盖创建、查询、确认、取消、事件发布与自动化测试。

## User Stories

- 客户可用幂等请求创建订单，并由服务端计算总额。
- 客户可分页查看自己的订单列表与详情。
- 客户可确认草稿订单。
- 客户可取消草稿或已确认订单。
- 测试人员可分别运行集成、契约、E2E、UAT 与压力测试资产。

## Design baseline

- [Requirement](../requirement.md)
- [Design](../design.md)
- [Test plan](../test-plan.md)

## Completion criteria

- 所有子 Issue 完成。
- 自动化测试与质量门禁通过。
- 文档和 ADR 与最终实现一致。

当前等待 API、数据库 schema 和模块/事件边界批准，因此实施工作保持 blocked。
