# 健身知识语料

本目录只保存经过人工整理的通用健身知识摘要，不保存用户档案、训练、饮食、体重、聊天历史、
Tool 返回值或其他个人数据。互联网资料只用于开发阶段核对来源，运行时检索不会联网。

## 文档格式与审核

每个 JSON 文件是一份独立资料，包含 `documentId`、`title`、`sourceName`、`sourceUrl`、
`publishedOrUpdatedAt`、`version`、`topics`、`language`、`riskLevel`、`reviewStatus`、
`reviewedAt`、`reviewer` 和项目自行整理的 `sections`。`APPROVED` 必须有审核日期；只有
`APPROVED` 文档会被切分和写入正式索引。`CANDIDATE` 仅用于待审清单，不能通过入库命令进入
Qdrant。

新增或升级资料时：

1. 核对官方或同行评审来源、发布日期、适用人群和版权边界。
2. 用项目自己的语言写简短摘要，不复制大段原文，不收录医疗诊断、伤病治疗或康复处方。
3. 先保存为 `CANDIDATE`；审核后更新为 `APPROVED`、填写 `reviewedAt` 和 `reviewer`，并提升
   `version`。
4. 运行 `make rag-eval` 和自动化测试。已有索引需要反映升级或删除时，使用明确受限的
   `make rag-rebuild-local`，不要手改 Qdrant 数据。

chunk ID 由文档编号、版本、章节和规范化正文生成稳定 SHA-256 值；相同版本重复执行入库会
upsert 同一 point，不产生重复数据。常规 `rag-index` 不删除旧 point；文档升级或删除后必须执行
本地重建命令。重建保护只允许 Collection 名称以 `fitness_knowledge_local` 开头。

主题名是运行时检索契约，应保持稳定。`KB_ACSM_PROGRESSION_2009` 使用 `rest_intervals`
表示组间休息与恢复时长；检索器会将“组间休息/组间歇/组间恢复/每组之间休息多久”
映射到该主题。本次优化未修改语料正文、版本或 chunk ID，现有 Qdrant Collection 无需重建。

## 当前清单

| 文档编号 | 来源 | 版本 | 状态 | 审核日期 |
| --- | --- | --- | --- | --- |
| `KB_HHS_PHYSICAL_ACTIVITY_2018` | U.S. HHS | `2018-2ed-project-summary-v1` | APPROVED | 2026-10-12 |
| `KB_ACSM_RESISTANCE_2026` | ACSM | `2026-position-stand-project-summary-v1` | APPROVED | 2026-10-12 |
| `KB_ACSM_PROGRESSION_2009` | ACSM | `2009-position-stand-project-summary-v1` | APPROVED | 2026-10-12 |
| `KB_ISSN_PROTEIN_2017` | ISSN | `2017-position-stand-project-summary-v1` | APPROVED | 2026-10-12 |
| `KB_CDC_WEIGHT_2025` | U.S. CDC | `2025-01-project-summary-v1` | APPROVED | 2026-10-12 |
| `KB_AHA_WARMUP_COOLDOWN_2024` | AHA | `2024-01-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_RESISTANCE_WARMUP_REVIEW_2021` | Ribeiro et al. | `2021-review-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_MAYO_SQUAT_2025` | Mayo Clinic | `2025-11-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_MAYO_LUNGE_2025` | Mayo Clinic | `2025-11-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_ACE_DEADLIFT_2024` | ACE | `2024-12-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_MAYO_PUSHUP_2025` | Mayo Clinic | `2025-11-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_MAYO_SEATED_ROW_2025` | Mayo Clinic | `2025-11-project-summary-v1` | APPROVED | 2026-10-14 |
| `KB_MAYO_LAT_PULLDOWN_2025` | Mayo Clinic | `2025-11-project-summary-v1` | APPROVED | 2026-10-14 |
| `CANDIDATE_NHS_COOLDOWN_2023` | NHS | `2023-12-candidate-v1` | CANDIDATE | 未审核 |

当前共有 13 份 APPROVED 文档、40 个稳定 chunk，覆盖力量训练原则、频率、组次、RPE/强度表达、
组间休息、热身/冷身、抗阻训练专项热身，以及深蹲、弓步、髋铰链/硬拉、水平推、水平拉和垂直拉
基础动作模式。动作知识是项目自行整理的短摘要，不复制来源长段正文，也不提供伤病治疗或康复处方。

具体动作编号、器械、动作步骤、安全提示、常见错误、限制标签和替代动作仍以 Java 动作目录为权威；
RAG 只补充有来源引用的通用动作模式知识，避免复制一份会与业务目录漂移的动作数据库。候选资料不会
出现在索引、回答或引用中。
