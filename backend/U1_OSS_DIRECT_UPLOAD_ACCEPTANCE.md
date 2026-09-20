# U0+U1 OSS 直传控制台配置与验收

> 当前只验收上传可观测性、授权和确认接口。小程序编辑页仍使用 legacy multipart；验收通过前不要开始 U2，也不要关闭旧公网入口。本文中的尖括号内容必须替换为你自己的值，禁止把真实密钥写回仓库。

## 1. 数据库迁移

在当前云端 `fitness_diary` 数据库执行：

`migrations/20260920_image_upload_tickets.sql`

执行后核对：

```sql
SHOW TABLES LIKE 'image_upload_tickets';
SHOW CREATE TABLE image_upload_tickets;
```

预期存在主键 `ticket_id`、唯一索引 `uk_image_upload_ticket_object_key`、用户/状态/过期时间联合索引和 `users(id)` 外键。不要清空或重建现有库。本地库只有在需要本地联调 U1 时才执行同一迁移；本轮不要求重放其他迁移。

## 2. RAM 专用角色

在阿里云 RAM 控制台创建自定义策略，例如 `FitnessOssDirectUploadRolePolicy`。将 `<BUCKET>` 替换为现有 Bucket 名：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "oss:PutObject",
        "oss:GetObjectMeta",
        "oss:GetObject",
        "oss:DeleteObject"
      ],
      "Resource": [
        "acs:oss:*:*:<BUCKET>/fitness-diary/images/*"
      ]
    }
  ]
}
```

然后：

1. 创建“阿里云账号”类型的 RAM 角色，例如 `FitnessOssDirectUploadRole`，最大会话时长保持至少 900 秒。
2. 将上面的自定义策略授予该角色，不授予 `ListBuckets`、`ListObjects`、Bucket 管理或其他目录权限。
3. 角色信任策略只信任云托管当前使用的 RAM 用户；不要信任外部账号或不相关服务。
4. 给云托管当前 RAM 用户增加以下自定义策略。不要用宽泛的 `AliyunSTSAssumeRoleAccess` 替代精确角色限制：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Resource": "acs:ram::<ACCOUNT_ID>:role/fitnessossdirectuploadrole"
    }
  ]
}
```

代码还会把每次 STS session policy 进一步缩小到当前用户的单个随机 Object Key。

## 3. OSS Bucket

1. 保持现有 Bucket，禁止公共写入；不要把 ACL 改成 `public-read-write`。
2. 记下外网 HTTPS Bucket 域名，例如 `https://<BUCKET>.oss-<REGION>.aliyuncs.com`，代码中的 Endpoint 仍填写不含 Bucket 名的地域 Endpoint。
3. 若已配置防盗链，允许微信小程序 Referer（`*servicewechat.com`），并按现有图片展示方式决定是否允许空 Referer。
4. 建议添加最小 CORS 规则：来源 `https://servicewechat.com`，方法 `POST`，允许 Headers `*`，暴露 Headers `ETag`、`x-oss-request-id`，缓存 600 秒。不要为生产长期使用来源 `*`；若开发者工具报告 CORS，再按实际 `Origin` 增加一条测试来源规则。

## 4. 云托管环境变量与部署

保留已验证的数据库、微信登录、Token 和现有 OSS 配置，只新增/核对：

```text
FITNESS_OSS_ENDPOINT=https://oss-<REGION>.aliyuncs.com
FITNESS_OSS_REGION=<REGION，例如 cn-beijing>
FITNESS_OSS_STS_ROLE_ARN=acs:ram::<ACCOUNT_ID>:role/fitnessossdirectuploadrole
FITNESS_OSS_UPLOAD_TICKET_TTL_SECONDS=300
FITNESS_OSS_UPLOAD_TICKET_CLEANUP_DELAY_MS=60000
```

`FITNESS_OSS_ACCESS_KEY_ID`、`FITNESS_OSS_ACCESS_KEY_SECRET`、`FITNESS_OSS_BUCKET_NAME` 和 `FITNESS_OSS_IMAGE_DIR=fitness-diary/images/` 保持现有正确值。部署新版本使用项目已生成的：

`deploy/fitness-cloud-poc-backend.zip`

部署后先确认 `/api/health` 正常，登录和普通 JSON API 无回归，再进行 U1 验收。

## 5. 微信公众平台

在“开发管理 → 开发设置 → 服务器域名”中，把 OSS 外网 HTTPS Bucket 域名加入：

- `uploadFile` 合法域名；
- `downloadFile` 合法域名（若当前图片展示已依赖该域名，保留原配置）。

只填域名，不带 Object 路径、查询串或末尾文件名。配置后重新编译小程序，并清理开发者工具缓存。

## 6. U0 可观测性验收

在现有菜谱编辑页选择一张小图触发一次 legacy 上传；本轮不要求旧链路变快。

1. 开发者工具 Console 搜索 `IMAGE_UPLOAD_OBSERVABILITY`。
2. 应看到同一个 `uploadAttemptId` 下的阶段、字节数、耗时和稳定错误类别：`legal_domain_blocked`、`timeout`、`user_cancelled`、`authorization_failed`、`invalid_response`、`server_response` 或 `native_network_error`。
3. 云托管日志搜索 `IMAGE_UPLOAD_`，应能按相同编号关联到 legacy、ticket 或 confirm 阶段。
4. 日志中不得出现本地临时路径、Token、STS 临时凭证、Policy、Object Key、图片 URL、请求正文或健康数据。

## 7. U1 授权、直传和确认验收

由于 U2 尚未开始，使用开发者工具 Console 做一次手工链路验证。先把脚本开头的环境 ID 和服务名替换为 `config/runtime.js` 中当前值；脚本不会打印 Token、Policy、Object Key 或临时凭证。

```js
(async () => {
  const envId = '<当前云环境ID>';
  const serviceName = '<当前云托管服务名>';
  const token = wx.getStorageSync('authToken');
  if (!token) throw new Error('请先在小程序中完成登录');
  const attempt = 'upl_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 12);
  const call = (path, data) => new Promise((resolve, reject) => wx.cloud.callContainer({
    config: { env: envId },
    path: '/api' + path,
    method: 'POST',
    data,
    header: {
      'content-type': 'application/json',
      'Authorization': 'Bearer ' + token,
      'X-WX-SERVICE': serviceName,
      'X-Request-Id': attempt
    },
    success: (res) => res.statusCode === 200 && res.data && res.data.code === 200
      ? resolve(res.data.data) : reject(res),
    fail: reject
  }));
  const chosen = await new Promise((resolve, reject) => wx.chooseImage({
    count: 1, sizeType: ['compressed'], sourceType: ['album'], success: resolve, fail: reject
  }));
  const file = chosen.tempFiles[0];
  const info = await new Promise((resolve, reject) => wx.getImageInfo({
    src: file.path, success: resolve, fail: reject
  }));
  const ticket = await call('/image/upload-ticket', {
    fileSize: file.size,
    format: info.type,
    uploadAttemptId: attempt
  });
  await new Promise((resolve, reject) => wx.uploadFile({
    url: ticket.host,
    filePath: file.path,
    name: 'file',
    formData: ticket.formFields,
    success: (res) => res.statusCode === 204 ? resolve() : reject(res),
    fail: reject
  }));
  const confirmed = await call('/image/upload-confirm', {
    ticketId: ticket.ticketId,
    uploadAttemptId: attempt
  });
  globalThis.u1Acceptance = { ticketId: ticket.ticketId, imageUrl: confirmed.imageUrl, attempt };
  console.log('U1_OK', { status: 'confirmed', attempt });
})().catch(error => console.error('U1_FAILED', error));
```

通过条件：

1. Console 只输出 `U1_OK`，OSS 上传 HTTP 状态为 204，确认接口返回 HTTPS `imageUrl`。
2. `image_upload_tickets` 对应行从 `ISSUED` 变为 `CONFIRMED`；`object_key` 必须是 `fitness-diary/images/<当前用户数字ID>/<UUID>.<格式>`。
3. 用同一账号再次确认 `globalThis.u1Acceptance.ticketId`，必须返回 409；换另一个微信账号确认同一 ticket，必须返回 403。
4. 申请一个 ticket 后不上传，超过 5 分钟再确认，必须返回 409；数据库最终为 `EXPIRED`。定时任务日志只记录清理数量，不记录 key。
5. 在 OSS 控制台确认没有同名覆盖、没有写到其他目录、没有 SVG/HTML 对象。完成测试后，用原账号调用现有 `/image/delete` 删除未被菜谱引用的测试图。
6. 云托管日志中 `IMAGE_UPLOAD_TICKET_*` 与 `IMAGE_UPLOAD_CONFIRM_*` 目标耗时不超过 800 ms；冷启动单独记录。

请回传：U0 客户端错误类别、ticket/OSS/confirm 三段结果、重复确认状态、跨账号状态、过期状态，以及对应 `uploadAttemptId`。不要回传 Token、Policy、Object Key、图片 URL 或任何密钥。
