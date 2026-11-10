# Fitness Diary Backend

Java 17 / Spring Boot 3 后端，负责微信认证、菜谱与饮食、训练、阶段分析和 Agent 的业务接口。
使用 MyBatis 与 Spring Data JPA 访问 MySQL；Python Agent 不能直接访问业务数据库。

## 配置与启动

从仓库根目录复制配置，仅在副本中填写真实值：

```bash
cp backend/.env.example backend/.env
cd backend
mvn spring-boot:run
```

Spring Boot 从 `backend` 工作目录读取 `.env`。默认接口前缀为 `http://127.0.0.1:8080/api`，
健康检查为 `/api/health`。IDE 启动时也应将工作目录设为 `backend`。

| 配置 | 用途 |
| --- | --- |
| `FITNESS_DB_URL`、`FITNESS_DB_USERNAME`、`FITNESS_DB_PASSWORD` | MySQL 连接 |
| `FITNESS_OSS_*` | 图片对象存储 |
| `FITNESS_WECHAT_MOCK_ENABLED` | 本地模拟微信登录，正式环境必须关闭 |
| `FITNESS_WECHAT_APP_ID`、`FITNESS_WECHAT_APP_SECRET` | 微信登录 |
| `FITNESS_AUTH_TOKEN_SECRET` | 登录令牌签名密钥 |
| `FITNESS_PRODUCTION_MODE` | 正式环境启动校验 |
| `FITNESS_AGENT_ENABLED`、`FITNESS_AGENT_BASE_URL` | Agent 功能开关与服务地址 |
| `FITNESS_AGENT_SERVICE_TOKEN`、`FITNESS_AGENT_CONTEXT_SECRET` | 服务认证与短时效用户上下文 |

完整变量与默认值见 [.env.example](.env.example)。正式环境通过平台环境变量配置凭证，
启用 Agent 时服务令牌与上下文签名密钥必须是独立的强随机值，满足启动校验要求。

## 数据库

`database.sql` 是基础结构与开发种子数据，包含 `test_openid` 测试账号，不能对已有数据库重新执行。
`migrations/` 保存增量结构和参考数据变更，不能只导入基础脚本就启动最新代码。

对于全新、独立且允许初始化的数据库，`deployment/bootstrap-cloud-poc.sh` 会拒绝非空库，
导入基础脚本和脚本清单中的迁移，并移除测试账号。该清单目前截至 `20261010`；
启动最新 Agent 和训练热量功能前，还需依次执行：

1. `migrations/20261011_ai_agent_conversations.sql`
2. `migrations/20261015_unify_workout_calorie_estimation.sql`
3. `migrations/20261016_add_exercise_calorie_duration.sql`

已有数据库必须先备份、核对已执行迁移，再按依赖补齐缺项；不要重复执行所有迁移，
也不要运行全量初始化脚本。基础结构与关系见 [database-design.md](database-design.md)，
最终结构以 SQL 及增量迁移为准。

## 业务边界

- 登录从微信身份映射到业务账号；业务接口从 Bearer Token 取得当前用户，不信任客户端 `userId`。
- 菜谱与训练使用公开业务 ID，数据库自增主键保留在服务端。
- 食物识别只产生草稿；保存前根据标准食材 ID、重量与份数重新计算营养。
- 保存菜谱并入账在同一事务内完成，请求标识与唯一约束用于防止重复入账。
- 训练草稿受器械、限制、排除动作和 RPE 规则约束；确认后才保存为计划。
- 训练热量按 MET、参考体重与时长在服务端计算，并保存计算快照。
- Agent 内部工具使用服务令牌和签名用户上下文；公共 MCP 动作目录使用独立的公共数据读取路径。
- 模型调用不应占用长事务；会话历史由 Java 按资源归属读取与持久化。

## 验证与构建

```bash
mvn test
mvn package
java -jar target/fitness-diary-0.0.1-SNAPSHOT.jar
```

自动化使用 H2、Mock HTTP 和 Mock OSS；微信登录、真实上传、模型推理及云端环境需要单独联调。
部署相关脚本与 Dockerfile 位于 `deployment/` 和后端根目录。当前最新 Agent 版本尚未部署腾讯云，
云端迁移状态必须单独核对，不能根据本地成功运行推断。
