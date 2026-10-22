# Add Docker Compose and CI quality gates

建立本地容器化环境和分层 CI 门禁。

## Acceptance criteria

- 后端和前端使用多阶段镜像、非 root 用户和 healthcheck。
- Compose 默认支持应用、Web、PostgreSQL 和 Kafka。
- Redis 与 Elasticsearch 仅作为可选 profile。
- CI 顺序为单元/静态/架构检查，再到集成/契约和 E2E/UAT。
- 压力测试按需或夜间运行，不阻塞普通 PR。
- 不包含 Kubernetes 或生产部署变更。

## Dependencies

Blocked by persistence, REST API and frontend; Kafka profile与事件发布协同。
