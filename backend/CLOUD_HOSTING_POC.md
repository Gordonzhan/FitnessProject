# 微信云托管 PoC 操作手册

本手册只覆盖第十项阶段 A：将现有 Spring Boot 后端部署到微信云托管测试环境，连接一套全新的测试数据库，完成真机验收后暂停。登录与普通 JSON API 已在 2026-09-19 验证通过；图片公网 multipart 上传在开发者工具和真机均出现超时，后续改造以项目根目录 `ITEM10_IMAGE_UPLOAD_AND_LAUNCH_PLAN.md` 为准。不要把本地正式数据、`.env` 或任何密钥上传到控制台。

## 1. 创建云环境和测试数据库

1. 在微信开发者工具中打开“云开发”，为当前小程序 AppID 创建一个测试环境，记录**环境 ID**。
2. 在同一云环境中开通 MySQL，数据库名固定为 `fitness_diary`。云托管服务和数据库应处于同一地域及私有网络。
3. 创建测试数据库账号。PoC 可先授予 `fitness_diary` 库的建表、改表、索引及读写权限；正式上线前再拆分迁移账号和最小权限业务账号。
4. 如需从本机初始化数据库，临时开启数据库外网连接并限制来源 IP；初始化后立即关闭。更推荐在同网络的受控环境执行。

首次初始化只能针对空库。在已安装 MySQL 客户端的终端执行：

```bash
cd /path/to/FitnessProject/backend
export FITNESS_DB_HOST='控制台显示的数据库地址'
export FITNESS_DB_PORT='3306'
export FITNESS_DB_USERNAME='测试数据库账号'
./deployment/bootstrap-cloud-poc.sh
```

脚本会安全提示输入密码，不要把密码发到聊天或写入命令历史。它会拒绝非空数据库，依次完成结构、迁移和数据校验，并删除 `test_openid` 本地测试账号。不要对已有业务数据库执行 `database.sql`。

## 2. 创建云托管服务

1. 在项目根目录生成最小源码部署包。Dockerfile 使用国内 Maven 镜像，并把依赖与源码分层以便构建缓存复用：

   ```bash
   cd /path/to/FitnessProject
   ./backend/deployment/package-cloud-poc.sh
   ```

2. 在云托管中新建服务，建议服务名 `fitness-api`，上传 `deploy/fitness-cloud-poc-backend.zip`，构建方式选择 Dockerfile，监听端口 `8080`。
3. PoC 资源从 1 核 2 GB、最小实例 0、最大实例 1 起步；健康检查路径填写 `/api/health`。首次冷启动会比常驻实例慢，验收时先访问一次健康检查。
4. 为了兼容旧 `wx.uploadFile` multipart 图片上传，PoC 曾暂时开启服务公网访问并记录其 HTTPS 默认域名。普通 JSON 接口仍通过 `wx.cloud.callContainer` 调用。该公网上传链路已确认不满足稳定性和性能要求，只能作为迁移期回退；正式方案为后端短期授权、客户端受限直传 OSS、后端确认对象，验收后再关闭公网入口或移除上传对它的依赖。

## 3. 配置后端环境变量

在云托管版本配置中添加以下变量。真实值只填入云控制台，不写入代码或文档：

```properties
FITNESS_PRODUCTION_MODE=true
FITNESS_WECHAT_MOCK_ENABLED=false
FITNESS_WECHAT_APP_ID=当前小程序AppID
FITNESS_WECHAT_APP_SECRET=当前小程序AppSecret
FITNESS_AUTH_TOKEN_SECRET=至少32字节的独立随机值

FITNESS_DB_URL=jdbc:mysql://内网数据库地址:3306/fitness_diary?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
FITNESS_DB_USERNAME=业务数据库账号
FITNESS_DB_PASSWORD=业务数据库密码
FITNESS_DB_POOL_MAX_SIZE=5
FITNESS_DB_POOL_MIN_IDLE=1

FITNESS_COMPARISON_INCLUDE_SYNTHETIC=false
FITNESS_COMPARISON_REAL_SNAPSHOT_ENABLED=false
FITNESS_COMPARISON_HASH_SECRET=至少32字节且与Token密钥不同的随机值
FITNESS_BUSINESS_ZONE=Asia/Shanghai

FITNESS_OSS_ENDPOINT=现有OSS Endpoint
FITNESS_OSS_REGION=OSS地域ID（例如 cn-beijing）
FITNESS_OSS_ACCESS_KEY_ID=最小权限RAM AccessKey ID
FITNESS_OSS_ACCESS_KEY_SECRET=最小权限RAM AccessKey Secret
FITNESS_OSS_BUCKET_NAME=现有Bucket名称
FITNESS_OSS_IMAGE_DIR=fitness-diary/images/
FITNESS_OSS_STS_ROLE_ARN=OSS直传专用RAM角色ARN
FITNESS_OSS_UPLOAD_TICKET_TTL_SECONDS=300
FITNESS_OSS_UPLOAD_TICKET_CLEANUP_DELAY_MS=60000
```

`PORT` 由平台注入，无需手工设置。部署成功后先在版本日志确认没有打印密钥，再访问公网 `https://服务默认域名/api/health`，应返回业务码 200。

## 4. 切换小程序到 PoC 环境

只修改项目根目录下的 `config/runtime.js`：

```js
transport: 'cloud',
cloud: {
  envId: '你的云环境ID',
  serviceName: 'fitness-api',
  apiPrefix: '/api',
  uploadBaseUrl: 'https://你的云托管公网默认域名'
}
```

环境 ID、服务名和默认域名不是密钥；AppSecret、数据库密码、Token/HMAC 密钥绝不能写到小程序代码中。

## 5. 阶段 A 验收

1. 在云托管控制台开启“开放接口服务”，把 `/wxa/getopendata` 加入允许调用的接口列表。该配置属于云托管开放接口权限，不是服务环境变量。
2. 开发者工具执行“清缓存并重新编译”，Network 中普通业务请求应由云托管调用完成，不再访问本机 IP 或 Bonjour 地址。
3. 首次进入自动微信登录并创建新账户；退出重进应复用同一账户，后端日志不得出现 code、Token、openid、CloudID 或 AppSecret。
4. 进入微信步数页并点击同步。小程序会优先发送五分钟有效的 CloudID，后端通过云托管内网 `http://api.weixin.qq.com/wxa/getopendata` 换取数据；这里使用 HTTP 是微信云托管开放接口服务的内部调用方式，不要改成公网 HTTPS，也不要为它配置合法域名。
5. 依次打开菜谱、训练、能量、阶段分析和人群对比页面，确认已验收功能无回归。
6. 用开发者工具和手机体验版各同步一次；成功后日志不应出现 `WECHAT_CODE_SESSION_UNAVAILABLE`。若出现 `WECHAT_OPEN_DATA_REJECTED`，优先检查开放接口权限和 CloudID 是否超过五分钟。
7. 验收完成后先暂停。此时尚未完成生产数据迁移、备份恢复、告警、密钥轮换、多实例定时任务保护或正式发布。

### 微信登录出现 502 的排查

- 日志为 `ResourceAccessException rootCause=SunCertPathBuilderException` 时，是容器访问 `jscode2session` 的证书信任链错误，不是 AppID、AppSecret 或 Token 密钥错误。当前部署包在云模式登录时直接采用 `callContainer` 网关注入身份，并校验注入 AppID；非云请求才回退到该接口。
- 公网出口保持开启即可，不需要为了该错误开启 VPC，也不要关闭 HTTPS 证书校验。
- 新版本启动日志出现 `Using a temporary truststore at ...` 和 `Picked up JAVA_TOOL_OPTIONS: ...javax.net.ssl.trustStore=...` 属于非 root 模式下的预期行为。
- 若上述两条启动日志已出现但微信步数仍发生 `SunCertPathBuilderException`，说明请求仍走了旧的 `code + encryptedData` 回退路径。先部署包含 CloudID 接口的后端，再上传包含 CloudID 参数的小程序版本，并开启 `/wxa/getopendata` 权限；不再继续修改 Java truststore。
- 发布后清除小程序本地 Token 并重新编译，再观察 `/api/user/login`。成功时不应再出现 `WECHAT_CODE_SESSION_UNAVAILABLE`；若出现 `WECHAT_CLOUD_IDENTITY_REJECTED`，检查云环境与小程序 AppID 的关联关系。

官方参考：

- [小程序访问云托管](https://docs.cloudbase.net/run/develop/access/mini)
- [获取、传输微信开放数据](https://docs.cloudbase.net/practices/get-wechat-open-data)
- [云托管服务设置](https://docs.cloudbase.net/run/deploy/service-setting)
- [云托管公网访问](https://docs.cloudbase.net/run/deploy/networking/public)
- [云托管连接 MySQL](https://docs.cloudbase.net/run/develop/resource-integration/mysql)
