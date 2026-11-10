const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..');
const runtimePath = path.join(projectRoot, 'config', 'runtime.js');
const forbiddenSecretKey = /(password|secret|token|access.?key|app.?secret|private.?key)/i;

/** 返回所有配置错误；空数组表示当前档案可以用于对应环境。 */
function validateRuntime(runtime, expectedProfile) {
  const errors = [];
  if (!runtime || typeof runtime !== 'object') return ['runtime.js 未导出有效配置对象'];
  if (!['local', 'production'].includes(runtime.profile)) errors.push('profile 必须是 local 或 production');
  if (expectedProfile && runtime.profile !== expectedProfile) {
    errors.push(`当前档案是 ${runtime.profile || '未知'}，预期为 ${expectedProfile}`);
  }

  findForbiddenKeys(runtime, '', errors);
  if (runtime.profile === 'local') validateLocal(runtime, errors);
  if (runtime.profile === 'production') validateProduction(runtime, errors);
  return errors;
}

/** 客户端配置禁止出现任何看起来像凭证的字段，即使值当前为空。 */
function findForbiddenKeys(value, prefix, errors) {
  if (!value || typeof value !== 'object') return;
  Object.entries(value).forEach(([key, child]) => {
    const name = prefix ? `${prefix}.${key}` : key;
    if (forbiddenSecretKey.test(key)) errors.push(`客户端配置禁止包含敏感字段：${name}`);
    findForbiddenKeys(child, name, errors);
  });
}

function validateLocal(runtime, errors) {
  if (runtime.transport !== 'local') errors.push('本地档案的 transport 必须是 local');
  try {
    const url = new URL(runtime.localApiBaseUrl);
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) {
      errors.push('本地模拟器地址必须使用 127.0.0.1 或 localhost');
    }
    if (!url.pathname.endsWith('/api')) errors.push('本地 API 地址必须以 /api 结尾');
  } catch (_) {
    errors.push('localApiBaseUrl 不是有效 URL');
  }
}

function validateProduction(runtime, errors) {
  if (runtime.transport !== 'cloud') errors.push('生产档案的 transport 必须是 cloud');
  const serialized = JSON.stringify(runtime);
  if (/127\.0\.0\.1|localhost|\.local(?:[/:]|$)/i.test(serialized)) {
    errors.push('生产档案不能包含本机地址');
  }
  if (runtime.localApiBaseUrl) errors.push('生产档案的 localApiBaseUrl 必须为空');
  const cloud = runtime.cloud || {};
  if (!cloud.envId) errors.push('生产云环境 ID 不能为空');
  if (!cloud.serviceName) errors.push('生产云服务名不能为空');
  if (cloud.apiPrefix !== '/api') errors.push('生产 apiPrefix 必须是 /api');
  try {
    const uploadUrl = new URL(cloud.uploadBaseUrl);
    if (uploadUrl.protocol !== 'https:') errors.push('生产图片上传地址必须使用 HTTPS');
    if (uploadUrl.pathname !== '/' && uploadUrl.pathname !== '') {
      errors.push('生产图片上传地址只能填写域名，不能包含 /api 或其他路径');
    }
  } catch (_) {
    errors.push('生产图片上传地址不是有效 HTTPS URL');
  }
}

function expectedProfileFromArgs(args) {
  const index = args.indexOf('--expect');
  return index >= 0 ? args[index + 1] : undefined;
}

if (require.main === module) {
  try {
    delete require.cache[require.resolve(runtimePath)];
    const runtime = require(runtimePath);
    const errors = validateRuntime(runtime, expectedProfileFromArgs(process.argv.slice(2)));
    if (errors.length) {
      errors.forEach(error => console.error('配置检查失败：' + error));
      process.exitCode = 1;
    } else {
      console.log(`配置检查通过：${runtime.profile} / ${runtime.transport}`);
    }
  } catch (error) {
    console.error('读取 config/runtime.js 失败，请先运行 npm run dev:prepare 或 npm run release:prepare');
    process.exitCode = 1;
  }
}

module.exports = { validateRuntime };
