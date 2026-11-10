/** 微信开发者工具模拟器使用的本地联调配置；本文件不得加入任何密钥。 */
module.exports = Object.freeze({
  profile: 'local',
  transport: 'local',
  localApiBaseUrl: 'http://127.0.0.1:8080/api',
  cloud: Object.freeze({
    envId: '',
    serviceName: '',
    apiPrefix: '/api',
    uploadBaseUrl: ''
  })
});
