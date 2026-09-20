# 微信云托管日志与告警配置

> 适用环境：`<YOUR_CLOUD_ENV_ID>`；服务：`<YOUR_CLOUD_SERVICE_NAME>`。本文不记录任何密钥、openid、图片 URL 或健康数据。当前图片继续使用代理 multipart，U1 直传暂停。

## 1. 日志采集

进入“云托管 → 服务 → `<YOUR_CLOUD_SERVICE_NAME>` → 服务设置 → 日志设置”，保持：

```text
stdout
```

应用只向标准输出写日志，不需要配置容器内文件路径。发布新版本后，在“服务详情 → 日志”选择最新版本和最近 15 分钟，依次搜索：

```text
IMAGE_UPLOAD_LEGACY_SUCCEEDED
SLOW_REQUEST
SLOW_SQL
```

正常上传一张图片后应能找到第一项。后两项没有结果表示当前窗口没有慢请求或慢 SQL，不是配置失败。

## 2. 云托管指标告警

进入当前环境的“监控告警 → 告警策略 → 新建”。告警对象选择 `<YOUR_CLOUD_SERVICE_NAME>`；能选择“全部版本”时选择全部版本，确保以后新版本自动纳入。

建议先建以下策略：

| 策略名 | 维度 | 条件 | 持续时间 | 级别 |
| --- | --- | --- | --- | --- |
| `fitness-instance-abnormal` | 服务 | 异常实例个数 >= 1 | 1 分钟 | 紧急 |
| `fitness-http-errors` | 服务 | HTTP 错误次数 >= 10 | 5 分钟 | 警告 |
| `fitness-response-slow` | 服务 | 平均响应时间 >= 2000 ms | 连续 5 分钟 | 警告 |
| `fitness-cpu-high` | 版本 | CPU 使用率 >= 80% | 连续 5 分钟 | 警告 |
| `fitness-memory-high` | 版本 | 内存使用率 >= 80% | 连续 5 分钟 | 警告 |

如果控制台只提供固定统计周期，选择最接近的值，不要为了完全一致创建重复策略。开启恢复通知；同一未恢复事件的重复通知间隔建议 30 分钟。当前允许缩容到 0 时，不要创建“实例数为 0”告警。

至少配置一个你日常能收到的通知渠道（微信、短信或邮件），并使用控制台的“测试通知”确认接收人有效。

## 3. 应用日志检索与可选日志告警

以下关键词分组用于服务日志或 CLS 全文检索：

### 紧急服务错误

```text
UNEXPECTED_SERVER_ERROR
DATABASE_ACCESS_FAILED
WECHAT_CLOUD_IDENTITY_REJECTED
```

### 外部依赖与后台任务

```text
UPSTREAM_OSS_FAILED
IMAGE_SERVICE_FAILED
IMAGE_UPLOAD_LEGACY_FAILED
OSS_CLEANUP_PENDING
WECHAT_CODE_SESSION_UNAVAILABLE
WECHAT_CODE_SESSION_REJECTED
WECHAT_CODE_SESSION_INVALID_RESPONSE
AUDIT_LOG_RETENTION_FAILED
COMPARISON_SNAPSHOT_FAILED
```

### 性能

```text
SLOW_REQUEST
SLOW_SQL
```

`CLIENT_REQUEST_REJECTED` 和单次 `AUTHENTICATION_REJECTED` 多数是用户输入或过期 Token，不作为即时通知条件；只有短时间异常暴增时再调查。

若当前环境已经把云托管日志接入腾讯云 CLS，可为以上三组分别创建日志告警：最近 5 分钟匹配数量大于 0 时触发，性能组可放宽为大于 5。若尚未开通 CLS，本轮先完成云托管指标告警和日志检索，不为了此项额外开通付费服务。

## 4. 无害验收

1. 在通知渠道中发送一次测试通知，确认手机或邮箱收到。
2. 手机端正常上传一张小图片并保存测试菜谱。
3. 云托管日志搜索 `IMAGE_UPLOAD_LEGACY_SUCCEEDED`，确认能看到 `requestId`、`uploadAttemptId` 和耗时，但看不到图片 URL、Object Key、Token、openid 或请求正文。
4. 如果已经启用 CLS 日志告警，可临时创建测试策略：最近 5 分钟 `IMAGE_UPLOAD_LEGACY_SUCCEEDED` 数量大于 0。再上传一张图片并确认收到通知，随后删除该测试策略，避免每次成功上传都告警。
5. 在“告警历史”确认测试通知或测试策略有记录；无需故意制造数据库、OSS 或实例故障。

请只回传：五条指标策略是否启用、测试通知是否收到、成功上传日志是否可检索、日志中是否发现敏感字段。不要回传完整日志正文或任何密钥。
