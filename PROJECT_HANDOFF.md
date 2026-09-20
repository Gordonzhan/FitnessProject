# FitnessProject 新任务交接说明

> 用途：在新的 Codex 任务中快速恢复项目上下文，避免重复携带长对话。  
> 最近更新：2026-09-20。本文不得记录密码、AccessKey、微信密钥或其他真实凭证。

## 1. 当前协作规则

- 每轮只实现一个明确优化项；实现并完成自动化检查后必须停下，等待用户在微信开发者工具中验收。
- 用户明确回复“验证通过”后，才允许开始下一项。
- 不重新设计、重构或改动已经验收通过的功能，除非新需求明确要求或发现直接回归问题。
- 修改前先检查现有实现和 `backend/README.md`，保留用户已有改动。
- 涉及数据库结构时提供可重复执行的迁移脚本，并分别说明本地和服务器是否需要执行。
- 营养、热量、体重趋势及人群比较只能标注为健身参考，不能描述为医疗诊断。

## 2. 项目概况

- 工作目录：项目根目录（以下命令用 `$PROJECT_ROOT` 表示）
- 前端：原生微信小程序，入口文件在项目根目录，页面位于 `pages/`，公共选择器位于 `components/reference-selector/`，请求工具位于 `utils/`。
- 后端：`backend/`，Java 17、Spring Boot 3.2、Spring Security、Validation、MyBatis、Spring Data JPA、MySQL、Maven。
- 文件存储：阿里云 OSS；AccessKey 等只通过 `backend/.env` 或 IDEA 环境变量提供，禁止写入代码和文档。
- 本地 API：`http://localhost:8080/api`。
- 本地微信登录通常使用 `FITNESS_WECHAT_MOCK_ENABLED=true` 和测试用户 `test_openid`。
- 项目根目录当前不是 Git 仓库；不要假设可以依靠 Git 回退改动。

常用验证命令：

```bash
cd "$PROJECT_ROOT"
npm test

cd "$PROJECT_ROOT/backend"
mvn test
```

最近已知基线：后端 153 项测试通过，前端 71 项测试通过。后续若新增测试，以实际运行结果为准。

## 3. 已确认的重要业务规则

- 客户端只使用 `recipes.recipe_id` 和 `workouts.workout_id` 业务编号；数据库自增主键不得暴露给小程序。
- 每日饮食保存菜谱名称、三大营养素和热量快照；修改或删除菜谱不能改变历史摄入。
- 相同菜谱可以明确加入多份，但同一次请求通过 `requestId` 幂等；每条摄入记录可以单独移除。
- 系统菜谱为全局只读模板，不能覆盖或删除；用户可直接加入饮食，也可复制为自己的菜谱后编辑、删除。
- 菜谱目录中用户菜谱优先于系统菜谱，支持分页、搜索、来源、菜式和健身目标筛选。
- 未来日期保存为 `PLANNED` 训练计划，不计入实际消耗；只有 `COMPLETED` 记录计入能量分析；`CANCELLED` 保留但不计入。
- 图片上传单张业务上限 20MB；客户端并发最多 2 张，后端 OSS 上传并发最多 4 个。OSS 删除必须校验用户和数据库引用，并在业务事务提交后清理。
- 食材和动作库使用服务端分页、分类及模糊搜索；前端不能一次性加载或渲染完整数据集。
- 数据库审计只记录危险或敏感白名单事件，不记录普通查询、搜索、登录、请求正文或健康数据。

## 4. 两套优化编号（不要混淆）

### 原工程质量主线

- 第四项图片上传、删除、超时和并发：已验证。
- 第五项事务处理：已验证。
- 插入项“移除误加的今日饮食”：已验证。
- 第六项可扩展食材和动作库：已验证。
- 第七项营养快照、唯一约束和幂等：已验证。
- 第八项缓存、索引、慢查询和接口性能：已验证。
- 第九项参数校验、统一错误码和请求追踪：用户已验证通过。
- 第十项部署、安全与备份：云托管登录、现有代理图片上传和菜谱保存已由用户验证可用；云模式使用 `callContainer` 网关注入的 OpenID 并校验 AppID，非云环境保留 code 回退。慢速暂归因于 VPN 和大图，U1 直传代码保留为备选但暂停启用，不再要求 RAM/STS 配置，也不开始 U2。当前进入阶段 B 生产数据与安全基线。
- R6 真机同步后续暴露云容器访问 `jscode2session` 的 `SunCertPathBuilderException`；即使 Temurin 临时 truststore 已生效仍失败。当前修复改为小程序发送五分钟有效 CloudID，后端校验云网关注入身份后调用开放接口 `/wxa/getopendata`；传统密文只作非云回退。发布时先部署后端，再上传新版小程序，并在云托管开启该开放接口权限。

### 上线前补充路线 R1～R7

| 编号 | 内容 | 状态 |
| --- | --- | --- |
| R1 | 新用户引导与系统菜谱模板库 | 用户已验证通过 |
| R2 | 操作审计日志分级与减量 | 用户已验证通过 |
| R3 | 训练计划与实际训练记录分离 | 用户已验证通过 |
| R4 | 长列表与编辑页快捷新增入口 | 用户已验证通过 |
| R5 | 个人阶段性饮食与训练分析 | 用户已验证通过 |
| R6 | 微信运动步数与独立估算消耗 | 用户已验证通过 |
| R7 | 匿名人群百分位与脱敏训练排名 | 用户已验证通过 |

## 5. 最近完成项：R7 匿名人群百分位与脱敏训练排名

R1～R7、原第九项、云托管登录、现有图片上传与菜谱保存均已由用户验证可用。U1 数据表、接口和自动化保留但暂停启用；正常页面仍走代理 multipart，不再要求直传控制台配置。第十项阶段 B 的首个子项“生产数据库、OSS 与 R7 开关启动安全校验”已由用户验收通过；用户明确跳过数据库和 OSS 最小权限检查。当前进行日志与告警子项：代码侧已补齐稳定关键词并移除异步清理日志中的图片 URL，控制台步骤见 `backend/CLOUD_HOSTING_ALERTS.md`，等待自动化与用户配置验收。

R7 目标范围：

- 固定比较最近30个业务日期，指标仅为完成训练次数、实际训练天数和计划完成率；不以训练热量高低评价用户。
- 优先使用相同健身目标的活跃样本；少于10人时扩展到全部目标，仍不足10人则隐藏百分位。
- 当前用户近30天没有任何训练记录时不分配百分位，避免把缺失数据解释为低表现。
- 接口 `GET /api/comparison/training` 返回当前用户指标、整数百分位、样本量、分组口径，以及按完成训练次数生成的脱敏前10名。用户可在档案中选填展示昵称；匿名快照只保存首尾保留的掩码结果（如 `gordon → g****n`），他人次数仅显示区间；不返回原始昵称、participant key、openid、用户 ID 或他人精确明细。
- 迁移 `backend/migrations/20260918_population_comparison.sql` 新增独立的 `comparison_daily_samples`，不与 `users/workouts` 混合。
- 迁移 `backend/migrations/20260919_comparison_masked_display_name.sql` 增加用户自填展示昵称及样本掩码昵称字段；正式快照仅写掩码结果，原始昵称不进入对比表。
- 本地脚本 `backend/dev-data/r7_synthetic_samples.sql` 生成固定规则的15个合成人物×30天，共450行，标记为 `SYNTHETIC/R7_DEMO_V1`；重复执行仍为450行。清理脚本为 `r7_synthetic_samples_cleanup.sql`。
- 合成样本默认关闭；本地 `.env` 已设置 `FITNESS_COMPARISON_INCLUDE_SYNTHETIC=true` 供本轮验收，生产环境必须为 `false`。页面在包含合成样本时醒目标注 DEMO，不能对外宣称真实排名。
- 正式匿名样本任务默认关闭；显式设置 `FITNESS_COMPARISON_REAL_SNAPSHOT_ENABLED=true` 且提供至少32字符的独立 `FITNESS_COMPARISON_HASH_SECRET` 后，服务端每天聚合达到最小训练记录数的用户，以 HMAC 参与者键写入 `ANONYMIZED/R7_REAL_V1`，不复制 openid、用户 ID 或动作明细。

R7 完成条件：

- 样本阈值、目标分组回退、平分百分位和当前数据不足状态正确。
- 页面展示汇总位置、掩码昵称前10名和当前用户大致名次，不出现原始昵称、账号标识或精确明细；样本不足或当前用户无记录时隐藏榜单。
- 合成数据可重复生成和定向清理，且 `users/workouts` 中没有合成账号或训练。
- 后端、前端和数据库验收后才能结束 R7。

## 6. 数据库和资料留档

- 初始结构：`backend/database.sql`。
- 已有迁移脚本：`backend/migrations/`，文件名按日期排序执行；本地已执行到 `20260919_comparison_masked_display_name.sql`。部署服务器时仍应按 README 说明逐项核对并执行。
- U1 新增 `backend/migrations/20260920_image_upload_tickets.sql`；用户已在目标云库执行。U1 当前暂停，表保留为空不会影响代理上传；不删除、不回退。
- 系统菜谱现有 50 个模板、166 条食材快照和 50 张 OSS 封面，其中 20 个中式家常模板。
- 菜谱营养数据来源、计算口径和版本：
  - `backend/data-sources/system-recipes-2026.09-v1.md`
  - `backend/data-sources/system-recipes-2026.09-v2.md`
  - `backend/data-sources/system-recipes-2026.09-v3-zh.md`
  - `backend/data-sources/system-recipe-covers-2026.09.md`
- 完整实现记录、接口、迁移说明和各项验收步骤统一查看 `backend/README.md`；只读取当前任务相关章节，不要重新分析所有历史章节。
- 第十项微信云托管上线、安全、备份、境外验收及 Agent 预留计划：`ITEM10_CLOUD_HOSTING_ROLLOUT_PLAN.md`。
- 图片上传修复、OSS 直传、性能指标及正式上线衔接计划：`ITEM10_IMAGE_UPLOAD_AND_LAUNCH_PLAN.md`。
- 云托管阶段 A 控制台操作、环境变量和验收步骤：`backend/CLOUD_HOSTING_POC.md`。
- 新云库只使用 `backend/deployment/bootstrap-cloud-poc.sh` 初始化；该脚本已在隔离 MySQL 8 演练通过，会拒绝非空库并删除本地 `test_openid`。源码小包由 `backend/deployment/package-cloud-poc.sh` 生成到 `deploy/`，Dockerfile 通过国内 Maven 镜像构建并拆分依赖缓存层；不得直接上传整个项目或 `backend/.env`。云端依赖服务整体不可用时，保留 `backend/deployment/Dockerfile.prebuilt` 作为本地 JAR 兜底。

## 7. 新任务的推荐开场提示词

```text
请在 FitnessProject 项目根目录继续开发。先完整阅读项目根目录 PROJECT_HANDOFF.md 和 ITEM10_CLOUD_HOSTING_ROLLOUT_PLAN.md，只阅读 backend/README.md 中与当前阶段相关的章节。R1～R7、原第九项、云托管登录、现有图片上传和菜谱保存均已验证可用，不要重做或回退。图片 U1 直传代码保留但暂停，不配置 RAM/STS，也不开始 U2。当前继续第十项阶段 B 的生产数据与安全基线；每次只完成一个子项，自动化通过后停下来等用户验收。
```
