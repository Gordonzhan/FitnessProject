const runtime = require('../config/runtime');

let initialized = false;

function isCloudTransport() {
  return runtime.transport === 'cloud';
}

function validateCloudConfig() {
  if (!isCloudTransport()) return;
  if (!runtime.cloud || !runtime.cloud.envId || !runtime.cloud.serviceName) {
    throw new Error('云托管配置不完整：请填写环境 ID 和服务名称');
  }
  if (!wx.cloud || typeof wx.cloud.init !== 'function' || typeof wx.cloud.callContainer !== 'function') {
    throw new Error('当前微信基础库不支持云托管调用');
  }
}

/** 在小程序启动时初始化一次与当前 AppID 关联的云开发环境。 */
function initializeCloud() {
  if (!isCloudTransport() || initialized) return;
  validateCloudConfig();
  wx.cloud.init({ env: runtime.cloud.envId, traceUser: true });
  initialized = true;
}

module.exports = { runtime, isCloudTransport, validateCloudConfig, initializeCloud };
