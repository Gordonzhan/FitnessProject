/**
 * Copy this file to runtime.js and replace the placeholders for your environment.
 * Never put AppSecret, database credentials, access keys, or signing secrets here.
 */
module.exports = Object.freeze({
  // 开发者工具联调本地 Agent 时使用 local；体验版/正式版部署后再改为 cloud。
  transport: 'local',
  // 仅适用于 Mac 上的开发者工具模拟器；真机联调应改为 Mac 的局域网 IP。
  localApiBaseUrl: 'http://127.0.0.1:8080/api',
  cloud: Object.freeze({
    envId: '<YOUR_CLOUD_ENV_ID>',
    serviceName: '<YOUR_CLOUD_SERVICE_NAME>',
    apiPrefix: '/api',
    uploadBaseUrl: 'https://<YOUR_CLOUD_SERVICE_DOMAIN>'
  })
});
