**Priority / 优先级:** P1  
**Sequence / 顺序:** Independent / 独立执行  
**Dependencies / 依赖:** None; separate Catalog bounded context / 无；独立商品目录限界上下文  
**Status / 状态:** OPEN — NOT_STARTED / 已打开—未开始

## Goal / 目标
Create the first testable Product Catalog module to validate the GitHub Issue → Codex CLI → Pull Request → CI flow. / 创建首个可测试的商品目录模块，用于验证 GitHub Issue → Codex CLI → Pull Request → CI 流程。

## Tech stack / 技术栈
Java 17, Spring Boot 3, Maven, PostgreSQL, Flyway, JUnit 5, and MockMvc. Reserve `frontend/` for React + TypeScript. / 使用 Java 17、Spring Boot 3、Maven、PostgreSQL、Flyway、JUnit 5 与 MockMvc；为 React + TypeScript 保留 `frontend/`。

## Scope / 范围
1. Bootstrap a Spring Boot backend under `backend/`. / 在 `backend/` 下建立 Spring Boot 后端。
2. Model Product with UUID id, name, description, decimal-safe price, currency, stock quantity, and timestamps. / 建模包含 UUID、名称、描述、精确价格、币种、库存与时间戳的 Product。
3. Implement create, detail, and paginated-list product APIs under `/api/v1/products`. / 实现 `/api/v1/products` 的创建、详情与分页列表 API。
4. Use Spring Data JPA and Flyway for PostgreSQL. / PostgreSQL 使用 Spring Data JPA 与 Flyway。
5. Validate input and return consistent validation/404 responses. / 校验输入并返回一致的校验/404 响应。
6. Add local PostgreSQL Compose configuration and secret-free example environment variables. / 添加本地 PostgreSQL Compose 配置与不含密钥的环境变量示例。
7. Add backend tests, minimal GitHub Actions CI, and runnable documentation. / 添加后端测试、最小 GitHub Actions CI 与可运行文档。

## Acceptance criteria / 验收标准
- [ ] `mvn test` covers creation, listing, lookup, invalid input, and missing products. / `mvn test` 覆盖创建、列表、查询、非法输入与商品不存在。
- [ ] Prices use decimal-safe types; PostgreSQL migrations apply correctly. / 价格使用精确小数类型；PostgreSQL 迁移正确执行。
- [ ] Endpoints return consistent JSON and appropriate HTTP status codes. / 端点返回一致 JSON 与正确 HTTP 状态码。
- [ ] Setup instructions and CI checks are included. / 包含启动说明与 CI 检查。
- [ ] Checkout, payment, and production deployment remain out of scope. / 结算、支付与生产部署不在范围内。

## Delivery rules / 交付规则
Inspect the repository first, then use a feature branch, run tests, report results, and open a review PR. Never auto-merge or deploy. / 先检查仓库，再使用功能分支、运行测试、报告结果并创建 Review PR；禁止自动合并或部署。

## Definition of Done / 完成定义
A reviewable PR contains code, tests, schema migrations, and documentation. / 可审查 PR 包含代码、测试、数据库迁移与文档。
