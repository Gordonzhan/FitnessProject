# FitnessProject

健身饮食日记微信小程序，包含原生微信小程序前端和 Java 17 / Spring Boot 后端。

## 本地启动

1. 复制本地配置模板（真实配置文件不会提交到 Git）：

   ```bash
   cp project.config.example.json project.config.json
   cp config/runtime.example.js config/runtime.js
   cp backend/.env.example backend/.env
   ```

2. 在 `project.config.json` 中填写小程序 AppID，在 `config/runtime.js` 中选择本地或云端传输；后端凭证只填写到 `backend/.env` 或运行环境变量中。
3. 初始化 MySQL 数据库，细节见 [后端说明](backend/README.md)。
4. 启动后端：

   ```bash
   cd backend
   mvn spring-boot:run
   ```

5. 使用微信开发者工具导入项目根目录。

## 验证

```bash
npm test
cd backend && mvn test
```

## 部署文档

- [微信云托管 PoC 操作手册](backend/CLOUD_HOSTING_POC.md)：部署包、云数据库、环境变量和阶段 A 验收。
- [云托管日志与告警](backend/CLOUD_HOSTING_ALERTS.md)：生产日志、指标告警和无害验收。
- [上线、安全与备份计划](ITEM10_CLOUD_HOSTING_ROLLOUT_PLAN.md)：生产迁移、备份恢复、多实例和发布路线。
- [图片上传与上线计划](ITEM10_IMAGE_UPLOAD_AND_LAUNCH_PLAN.md)：图片链路及上线衔接。

生成云托管部署包时运行：

```bash
./backend/deployment/package-cloud-poc.sh
```

部署包会写入本地 `deploy/`，该目录已被 Git 忽略。不要把整个仓库、`.env`、开发者私有配置、日志或构建产物上传到云平台。

## 安全

提交或部署前请阅读 [SECURITY.md](SECURITY.md)。仓库只保存环境变量名和占位符，不保存真实密码、AppSecret、AccessKey、Token/HMAC 密钥、云环境标识或本机地址。
