/**
 * Copy this file to runtime.js and replace the placeholders for your environment.
 * Never put AppSecret, database credentials, access keys, or signing secrets here.
 */
module.exports = Object.freeze({
  transport: 'local',
  localApiBaseUrl: 'http://localhost:8080/api',
  cloud: Object.freeze({
    envId: '<YOUR_CLOUD_ENV_ID>',
    serviceName: '<YOUR_CLOUD_SERVICE_NAME>',
    apiPrefix: '/api',
    uploadBaseUrl: 'https://<YOUR_CLOUD_SERVICE_DOMAIN>'
  })
});
