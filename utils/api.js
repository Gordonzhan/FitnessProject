// 后端 API 统一入口：负责鉴权、错误转换和 401 后单次自动重试。
const AUTH_TOKEN_KEY = 'authToken';
const CLOUD_GET_RETRY_DELAY_MS = 800;
const { createUploadTask } = require('./image-upload');
const { runtime, isCloudTransport, validateCloudConfig } = require('./cloud-runtime');

/** 为小程序到 Java 的整条链路生成可检索的请求编号。 */
function createRequestId() {
  return 'mp-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}

/** 只把可能自行恢复的网关、网络和超时错误视为可重试错误。 */
function isRetryableCloudFailure(error) {
  const code = error && (error.errCode !== undefined ? error.errCode : error.code);
  const message = String((error && (error.errMsg || error.message)) || '');
  if (/forbidden|permission|not found|invalid env|invalid service|参数/i.test(message)) {
    return false;
  }
  return Number(code) === -1
    || /cloud\.callContainer:fail|network|timeout|timed out|socket|econn|网络/i.test(message);
}

/**
 * 记录完整云调用诊断信息，但不记录 Authorization、请求体或用户数据。
 * 真机 Toast 空间有限，详细信息应从开发者工具 Console 中按 requestId 查询。
 */
function logCloudFailure(error, context) {
  const details = {
    path: context.path,
    method: context.method,
    attempt: context.attempt,
    requestId: context.requestId,
    cloudRequestId: error && (error.requestId || error.requestID),
    errCode: error && (error.errCode !== undefined ? error.errCode : error.code),
    errMsg: error && (error.errMsg || error.message)
  };
  console.error('CLOUD_CALL_FAILED', details);
  return details;
}

/** 将云调用错误转换为适合页面展示、同时保留排查编号的文本。 */
function cloudFailureMessage(details) {
  const codePart = details.errCode !== undefined && details.errCode !== null
    ? '，错误码：' + details.errCode
    : '';
  const traceId = details.cloudRequestId || details.requestId;
  return '网络请求失败，请稍后重试' + codePart + (traceId ? '（请求编号：' + traceId + '）' : '');
}

/** 从本地缓存生成 Bearer Token 请求头。 */
function getAuthorizationHeader() {
  const token = wx.getStorageSync(AUTH_TOKEN_KEY);
  return token ? { Authorization: 'Bearer ' + token } : {};
}

/** 确保请求发出前已完成微信登录和服务端换取 Token。 */
function ensureAuthenticated() {
  if (wx.getStorageSync(AUTH_TOKEN_KEY)) {
    return Promise.resolve();
  }
  const app = getApp();
  if (!app || !app.ensureLogin) {
    return Promise.reject('登录模块尚未初始化');
  }
  return app.ensureLogin();
}

/**
 * 执行一次微信网络请求，并将统一响应结构转换为业务数据或可读错误。
 * 云模式只允许无副作用的 GET 在瞬时网络错误时重试一次。
 */
function sendRequest(url, method, data, requiresAuth, timeoutMs) {
  return new Promise((resolve, reject) => {
    const requestId = createRequestId();
    const handleSuccess = (res) => {
      if (res.statusCode === 401 || (res.data && res.data.code === 401)) {
        reject({ authExpired: true, message: '登录已失效，请重新登录' });
        return;
      }
      if (res.statusCode !== 200) {
        const message = (res.data && res.data.message) || ('请求失败：' + res.statusCode);
        const headers = res.header || {};
        const responseRequestId = headers['X-Request-Id'] || headers['x-request-id']
          || headers['X-Cloudbase-Request-Id'] || headers['x-cloudbase-request-id'];
        reject(message + (res.statusCode >= 500 && responseRequestId
          ? '（请求编号：' + responseRequestId + '）'
          : ''));
        return;
      }
      if (!res.data || res.data.code !== 200) {
        reject((res.data && res.data.message) || '服务端返回异常');
        return;
      }
      resolve(res.data.data);
    };
    const header = Object.assign(
      { 'content-type': 'application/json', 'X-Request-Id': requestId },
      requiresAuth ? getAuthorizationHeader() : {}
    );

    if (isCloudTransport()) {
      try {
        validateCloudConfig();
        const path = runtime.cloud.apiPrefix + url;
        // 写请求不自动重试，避免保存、删除等操作因响应丢失而被重复执行。
        const maxRetries = method === 'GET' ? 1 : 0;
        const callCloud = (attempt) => wx.cloud.callContainer({
          config: { env: runtime.cloud.envId },
          path,
          method,
          data,
          timeout: timeoutMs,
          header: Object.assign({ 'X-WX-SERVICE': runtime.cloud.serviceName }, header),
          success: handleSuccess,
          fail: (error) => {
            const details = logCloudFailure(error, { path, method, attempt, requestId });
            if (attempt <= maxRetries && isRetryableCloudFailure(error)) {
              console.warn('CLOUD_CALL_RETRY', { path, method, attempt, requestId });
              setTimeout(() => callCloud(attempt + 1), CLOUD_GET_RETRY_DELAY_MS);
              return;
            }
            reject(cloudFailureMessage(details));
          }
        });
        callCloud(1);
      } catch (error) {
        reject(error.message || String(error));
      }
      return;
    }

    wx.request({
      url: runtime.localApiBaseUrl + url,
      method,
      data,
      timeout: timeoutMs,
      header,
      success: handleSuccess,
      fail: (error) => reject('网络错误：' + ((error && error.errMsg) || '请求失败'))
    });
  });
}

/**
 * API 请求总入口；默认要求登录，遇到明确 401 时刷新登录态并且只重试一次。
 */
function request(url, method, data, options) {
  const settings = Object.assign({ requiresAuth: true, retryOnUnauthorized: true, timeoutMs: 45000 }, options || {});
  const ready = settings.requiresAuth ? ensureAuthenticated() : Promise.resolve();

  return ready
    .then(() => sendRequest(url, method, data, settings.requiresAuth, settings.timeoutMs))
    .catch((error) => {
      if (!error || !error.authExpired || !settings.requiresAuth || !settings.retryOnUnauthorized) {
        return Promise.reject(error && error.message ? error.message : error);
      }

      wx.removeStorageSync(AUTH_TOKEN_KEY);
      const app = getApp();
      return app.ensureLogin(true)
        .then(() => request(url, method, data, {
          requiresAuth: true,
          retryOnUnauthorized: false,
          timeoutMs: settings.timeoutMs
        }));
    });
}

/** 登录相关接口。 */
const AuthAPI = {
  login: (code) => request('/user/login', 'POST', { code }, { requiresAuth: false })
};

/** 用户档案相关接口。 */
const UserAPI = {
  getCurrent: () => request('/user/me', 'GET'),
  update: (user) => request('/user/update', 'POST', user),
  getDailyCalories: () => request('/user/get-daily-calories', 'POST')
};

/** A4 用户明确保存的训练偏好；不会自动创建或修改训练记录。 */
const TrainingPreferenceAPI = {
  get: () => request('/user/training-preferences', 'GET'),
  save: (preferences) => request('/user/training-preferences', 'POST', preferences)
};

/** 用户菜谱与统一菜谱目录相关接口。 */
const RecipeAPI = {
  getList: () => request('/recipe/list', 'GET'),
  searchCatalog: (keyword, goal, cuisine, source, page, size) => request('/recipe/catalog', 'GET', { keyword, goal, cuisine, source, page, size }),
  get: (recipeId) => request('/recipe/get/' + encodeURIComponent(recipeId), 'GET'),
  save: (recipe) => request('/recipe/save', 'POST', recipe),
  saveAndAddIntake: (payload) => request('/recipe/save-and-add-intake', 'POST', payload),
  deleteByRecipeId: (recipeId) => request('/recipe/delete/' + encodeURIComponent(recipeId), 'DELETE')
};

/** 训练记录、计划完成及取消相关接口。 */
const WorkoutAPI = {
  getList: () => request('/workout/list', 'GET'),
  getByDate: (date) => request('/workout/get/' + encodeURIComponent(date), 'GET'),
  estimate: (workout) => request('/workout/estimate', 'POST', workout),
  save: (workout) => request('/workout/save', 'POST', workout),
  complete: (workoutId, params) => request('/workout/complete/' + encodeURIComponent(workoutId), 'POST', params),
  cancel: (workoutId) => request('/workout/cancel/' + encodeURIComponent(workoutId), 'POST'),
  deleteByWorkoutId: (workoutId) => request('/workout/delete/' + encodeURIComponent(workoutId), 'DELETE')
};

/** 每日能量对比及饮食记录相关接口。 */
const CompareAPI = {
  getDailyData: (params) => request('/compare/get-daily-data', 'POST', params),
  addRecipe: (params) => request('/compare/add-recipe', 'POST', params),
  removeRecipe: (params) => request('/compare/remove-recipe', 'POST', params),
  removeIntake: (params) => request('/compare/remove-intake', 'POST', params)
};

/** R5 个人阶段分析与体重历史。 */
const AnalysisAPI = {
  getSummary: (startDate, endDate) => request('/analysis/summary', 'POST', { startDate, endDate }),
  recordWeight: (date, weight) => request('/analysis/weight', 'POST', { date, weight })
};

/** R6 微信运动步数；估算消耗与训练实际消耗保持独立。 */
const ActivityAPI = {
  getWechatSummary: () => request('/activity/wechat/summary', 'GET'),
  syncWechat: (payload) => request('/activity/wechat/sync', 'POST', payload),
  deleteWechatData: () => request('/activity/wechat', 'DELETE')
};

/** R7 匿名人群训练对比；榜单只返回统一匿名名称和区间值。 */
const PopulationComparisonAPI = {
  getTrainingComparison: () => request('/comparison/training', 'GET')
};

/** 基础食材库和动作库的搜索、分类与批量解析接口。 */
const DatabaseAPI = {
  getFoods: () => request('/database/food/list', 'GET'),
  getFood: (foodName) => request('/database/food/get/' + encodeURIComponent(foodName), 'GET'),
  getExercises: () => request('/database/exercise/list', 'GET'),
  getExercise: (name) => request('/database/exercise/get/' + encodeURIComponent(name), 'GET'),
  getExerciseByCode: (exerciseCode) => request('/database/exercise/code/' + encodeURIComponent(exerciseCode), 'GET'),
  searchFoods: (keyword, category, page, size) => request('/database/food/search', 'GET', { keyword, category, page, size }),
  searchExercises: (keyword, category, page, size, filters = {}) => request('/database/exercise/search', 'GET', {
    keyword, category, page, size,
    equipment: filters.equipment || '',
    excludedLimitations: filters.excludedLimitations || ''
  }),
  getFoodCategories: () => request('/database/food/categories', 'GET'),
  getExerciseCategories: () => request('/database/exercise/categories', 'GET'),
  resolveFoods: (names) => request('/database/food/resolve', 'POST', { names }),
  resolveExercises: (names) => request('/database/exercise/resolve', 'POST', { names }),
  resolveExerciseCodes: (exerciseCodes) => request('/database/exercise/resolve-codes', 'POST', { exerciseCodes })
};

/** OSS 图片上传和未引用图片删除接口。 */
const ImageAPI = {
  upload: (filePath, fileMeta) => createUploadTask(fileMeta || filePath, {
    url: isCloudTransport()
      ? requiredCloudUploadBaseUrl() + '/api/image/upload'
      : runtime.localApiBaseUrl + '/image/upload',
    getHeader: getAuthorizationHeader,
    ensureAuth: ensureAuthenticated,
    refreshAuth: () => {
      wx.removeStorageSync(AUTH_TOKEN_KEY);
      return getApp().ensureLogin(true);
    }
  }),
  issueUploadTicket: (fileSize, format, uploadAttemptId) => request('/image/upload-ticket', 'POST', {
    fileSize, format, uploadAttemptId
  }),
  confirmUpload: (ticketId, uploadAttemptId) => request('/image/upload-confirm', 'POST', {
    ticketId, uploadAttemptId
  }),
  delete: (imageUrl) => request('/image/delete', 'POST', { imageUrl })
};

/** AI 草稿接口允许本地视觉模型冷启动，但仍由 Java 和 Python 各自限制上游超时。 */
const AiAPI = {
  getStatus: () => request('/ai/status', 'GET'),
  createFoodDraft: (payload) => request('/ai/food-recognition/draft', 'POST', payload, { timeoutMs: 180000 }),
  createWorkoutDraft: (payload) => request('/ai/workout-draft', 'POST', payload, { timeoutMs: 180000 }),
  replaceWorkoutExercise: (payload) => request('/ai/workout-draft/replace', 'POST', payload, { timeoutMs: 180000 }),
  removeWorkoutExercise: (payload) => request('/ai/workout-draft/remove', 'POST', payload, { timeoutMs: 45000 }),
  confirmWorkoutDraft: (payload) => request('/ai/workout-draft/confirm', 'POST', payload, { timeoutMs: 180000 }),
  openAgentSession: () => request('/ai/agent/sessions', 'POST', {}, { timeoutMs: 45000 }),
  getAgentSession: (sessionId) => request('/ai/agent/sessions/' + encodeURIComponent(sessionId), 'GET'),
  clearAgentMessages: (sessionId) => request('/ai/agent/sessions/'
    + encodeURIComponent(sessionId) + '/messages', 'DELETE'),
  sendAgentMessage: (sessionId, content) => request('/ai/agent/sessions/'
    + encodeURIComponent(sessionId) + '/messages', 'POST', { content }, { timeoutMs: 180000 })
};

function requiredCloudUploadBaseUrl() {
  const value = runtime.cloud && runtime.cloud.uploadBaseUrl;
  if (!value || !/^https:\/\//.test(value)) {
    throw new Error('云托管图片上传地址未配置：请填写服务公网 HTTPS 域名');
  }
  return value.replace(/\/+$/, '');
}

module.exports = {
  AuthAPI,
  UserAPI,
  TrainingPreferenceAPI,
  RecipeAPI,
  WorkoutAPI,
  CompareAPI,
  AnalysisAPI,
  ActivityAPI,
  PopulationComparisonAPI,
  DatabaseAPI,
  ImageAPI,
  AiAPI
};
