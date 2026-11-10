# 安全说明

## 不得提交的内容

- `backend/.env` 或其他真实环境变量文件
- 微信 AppSecret、数据库密码、OSS AccessKey、Token/HMAC 密钥
- `project.config.json`、`project.private.config.json`、`config/runtime.js`
- 私有网络拓扑、IDE 配置、日志、崩溃转储、构建产物和部署 ZIP
- 个人数据、数据库导出、讨论规划、交接和验收笔记

小程序必需的公开 AppID、云环境 ID、服务名与 HTTPS 域名不是服务端凭证；配置模板应使用占位值。
`config/runtime.local.js` 和 `config/runtime.production.js` 仅保存客户端可见配置，不得加入密钥。

仓库提供 `project.config.example.json`、`config/runtime.example.js` 和 `backend/.env.example`。复制模板后只在被忽略的本地文件或部署平台的密钥/环境变量管理中填写真实值。

## 发布前检查

```bash
git status --short
git ls-files | rg '(^|/)(\.env|project\.config\.json|project\.private\.config\.json|runtime\.js)$|\.(pem|key|p12|pfx|log|zip)$'
```

第二条命令应无输出。若凭证曾进入 Git 历史，删除当前文件并不够；必须先轮换凭证，再清理仓库历史。

安全问题请私下报告给仓库维护者，不要创建包含凭证、个人数据或完整生产日志的公开 Issue。
