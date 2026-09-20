# 第十项：图片上传修复、提速与正式上线计划

> 状态：2026-09-19 用户确认现有代理上传与菜谱保存暂时可用，慢速主要与 VPN 和大图有关；U1 代码保留但暂停启用，不再要求 RAM/STS/直传配置，U2 不开始。  
> 边界：只处理云端图片上传及正式上线准备，不回退或重做已经验收的 R1～R7、登录、数据库初始化和菜谱保存逻辑。本文不得记录任何真实密钥。

## 1. 当前事实与问题定位

### 2026-09-19 决策更新

- 用户已重新验证现有 `wx.uploadFile -> 云托管 -> OSS` 上传及菜谱保存可用，当前不再把偶发慢速视为必须切换架构的阻塞问题。
- 历史样本无法排除 VPN、图片体积、云托管冷启动和默认公网入口波动；Controller 计时从 multipart 解析完成后才开始，不能单独证明 OSS 直传是唯一合理方案。
- U1 数据表、接口和测试保留为以后容量或稳定性不足时的备选能力；正常页面继续使用 legacy multipart。未重新出现稳定性问题前，不配置 STS 角色、不启用 U1、不推进 U2。
- 若问题再次出现，先用同一文件做 VPN 开关、冷/热实例和真机对照测试，再决定压缩、最小实例、自定义域名或直传，不直接扩大架构。

- 云托管登录已经验证通过；普通 JSON API 通过 `wx.cloud.callContainer` 正常工作。
- 当前图片仍使用 `wx.uploadFile -> 云托管默认公网域名 -> Spring multipart -> OSS`。这条链路与普通 API 不同，依赖 `config/runtime.js` 中的 `uploadBaseUrl` 和云托管公网入口。
- 开发者工具与手机均出现“图片上传超时”；当前截图未显示 HTTP 状态或云端同请求日志，不能把具体失败原因直接归为 OSS。
- 已有两次成功请求的历史测量：约 2.97 MB 文件在客户端耗时 18.42 秒，而后端从收到完整 multipart 到写入 OSS 仅约 1.44 秒；约 0.35 MB 文件在客户端耗时 42.20 秒，而后端仅约 0.39 秒。瓶颈主要发生在请求到达 Controller 之前，即小程序到云托管公网 multipart 链路。
- 云托管默认域名只适合开发测试，存在访问限制和风控风险；正式上线不应继续依赖它承载文件上传。

## 2. 备选直传架构（当前暂停）

普通业务接口继续走微信私有链路：

```text
小程序 -> wx.cloud.callContainer -> 云托管 Spring Boot -> MySQL
```

如果以后证据表明代理上传在热实例和正常网络下仍不达标，再考虑改为服务端授权的 OSS 直传：

```text
1. 小程序 -> callContainer -> 后端申请一次性上传授权
2. 小程序 -> wx.uploadFile -> OSS（文件不再经过云托管）
3. 小程序 -> callContainer -> 后端确认对象并返回正式 imageUrl
4. 保存菜谱继续使用现有接口；未保存图片继续走现有清理机制
```

采用阿里云推荐的 STS 临时凭证 + PostObject V4 Policy。长期 AccessKey 只保留在云托管环境变量中；临时权限必须限制到当前用户目录、单个随机 Object Key、允许的图片类型、20 MB 上限和短有效期。不得把长期 AccessKey Secret 写入小程序、响应日志或仓库。

## 3. 分阶段执行

### U0：补齐证据并恢复可观测性

状态：代码已完成，等待云端日志验收。

1. 在前端为每张上传生成不含用户隐私的 `uploadAttemptId`，记录压缩前后字节数、传输阶段、耗时和微信原生错误类别。
2. 后端授权与确认接口继续使用现有 `X-Request-Id`；日志只记录大小、耗时和错误类型，不记录 Token、临时凭证、Object Key、图片 URL 或用户健康数据。
3. 保留旧 multipart 接口作为短期回退，但标记为 legacy；不再通过无限增加超时时间掩盖问题。
4. 验收：能区分“授权失败、微信合法域名拦截、OSS 直传失败、确认失败、用户取消和真正超时”。

### U1：后端生成受限的 OSS 直传授权

状态：代码已完成，等待执行迁移、配置 RAM/OSS/云托管并验收接口；小程序尚未切换到直传。

新增两个认证接口，均通过 `callContainer` 调用：

- `POST /api/image/upload-ticket`：输入文件大小和客户端识别的格式；返回短期 PostObject V4 表单字段、OSS HTTPS host、服务端生成的随机 key、过期时间。
- `POST /api/image/upload-confirm`：输入 ticket 标识；后端核对签名授权记录、OSS Object 的存在性、大小和前 12 字节魔数，验证成功后才返回 `imageUrl`。

安全要求：

- key 固定为 `fitness-diary/images/{currentUserId}/{uuid}.{validatedFormat}`，客户端不能指定目录或用户 ID。
- Policy 限制 Bucket、精确 key、1～20 MB、允许的 MIME、短有效期并禁止同名覆盖。
- STS RAM 角色只授予目标 Bucket 指定前缀的 `PutObject/GetObjectMeta/GetObject/DeleteObject` 最小权限。
- ticket 单次使用、短期有效，并与当前用户绑定；确认失败或格式不符时删除对象。
- 保留现有 `belongsToUser`、菜谱引用检查和事务提交后清理规则。

### U2：小程序直传、压缩与进度体验

1. `wx.chooseImage` 后使用 `wx.compressImage` 做显式压缩；保留 20 MB 业务上限，但以 3 MB 作为推荐目标，超出时分级压缩并重新读取实际大小。
2. 调用 `upload-ticket`，然后用 `wx.uploadFile` 直接 POST 到 OSS，最后调用 `upload-confirm`。
3. 接入 `UploadTask.onProgressUpdate`，页面逐张显示百分比；批量仍最多 2 张并发。
4. 网络中断后只对同一 ticket/key 做一次安全恢复：先确认对象是否已经存在，再决定是否重传，避免产生重复孤儿对象。
5. 删除 `runtime.cloud.uploadBaseUrl` 的业务依赖；所有 JSON 授权/确认请求继续使用 `callContainer`。旧 multipart 仅保留一个版本的灰度回退开关，验收后关闭。

### U3：控制台和基础设施配置

需要用户在实现完成后配合：

1. 阿里云 RAM 创建专用 STS 角色和最小权限策略；现有云托管 RAM 用户仅增加 `sts:AssumeRole` 权限。
2. 云托管新增 `FITNESS_OSS_STS_ROLE_ARN`、OSS Region 等非客户端配置；不替换已经可用的数据库、微信登录和 Token 配置。
3. 微信小程序后台把 OSS HTTPS Bucket 域名加入 `uploadFile` 合法域名；图片展示域名继续保留在 `downloadFile` 合法域名。
4. OSS Bucket 按官方建议配置直传所需规则；生产 Bucket 保持禁止公共写入。
5. 直传验收通过后，关闭云托管公网入口或至少不再让图片上传依赖公网默认域名。普通 API 的 `callContainer` 不受公网开关影响。

### U4：自动化与真机验收

自动化必须覆盖：

- ticket 的用户隔离、目录限制、过期、大小/MIME 约束和重复确认。
- 图片魔数与扩展名不匹配、伪造 HTML/SVG、空文件、超大文件、OSS 不存在及确认失败清理。
- 前端压缩、上传进度、授权刷新、合法域名错误、超时确认、单次恢复、批量部分成功和页面离开取消。
- 现有菜谱保存、删除、未保存图片清理和图片所有权测试不得回归。

验收顺序：开发者工具 -> 同网络真机 -> 蜂窝网络 -> 至少两个不同微信用户账号 -> 境外目标网络。每轮测试 1 张小图、1 张 3～5 MB 图片和 5 张批量图片。

性能门槛（排除用户主动取消）：

- 3 MB 以内单图：国内正常 Wi-Fi/5G 中位数不超过 5 秒，P95 不超过 15 秒。
- 5 张批量：无丢图、无重复 Object、失败项可单独重试，页面保持可操作。
- 后端只处理签名和确认，不再接收完整图片；云托管日志中的授权/确认接口目标耗时 P95 不超过 800 ms（冷启动单独统计）。

## 4. 正式上线前剩余门槛

图片直传通过后，仍按第十项主计划完成：生产自定义域名/公网关闭决策、数据库自动备份与恢复演练、密钥轮换、日志告警、最小实例与成本策略、多用户隔离回归、体验版与审核版发布检查、境外访问验收。任何一步失败都不进入正式发布。

## 5. 新任务从哪里开始

新任务先完整阅读 `PROJECT_HANDOFF.md`，再阅读本文和 `backend/README.md` 的“微信云托管阶段 A”“图片模块”章节。首先实施 U0+U1；自动化通过后给出阿里云 RAM/OSS 与微信后台的明确配置步骤，然后停下等待用户验收，不要同时推进 U2 以后的其他上线事项。

官方依据：

- 微信云托管默认域名仅建议测试使用，生产建议自定义域名：<https://docs.cloudbase.net/run/deploy/networking/custom-domains>
- `callContainer` 不依赖云托管公网开关：<https://docs.cloudbase.net/run/deploy/service-setting>
- 阿里云服务端签名直传及 STS/PostObject：<https://help.aliyun.com/zh/oss/user-guide/obtain-signature-information-from-the-server-and-upload-data-to-oss>
- 微信小程序直传 OSS：<https://help.aliyun.com/zh/oss/user-guide/wechat-applet-uploads-files-directly-to-oss>
