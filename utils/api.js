// 后端 API 统一入口：负责鉴权、错误转换和 401 后单次自动重试。
const AUTH_TOKEN_KEY = 'authToken';
const { createUploadTask } = require('./image-upload');
const { runtime, isCloudTransport, validateCloudConfig } = require('./cloud-runtime');

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
 * 本方法不负责重试，防止调用链产生无限重试。
 */
function sendRequest(url, method, data, requiresAuth) {
  return new Promise((resolve, reject) => {
    const handleSuccess = (res) => {
      if (res.statusCode === 401 || (res.data && res.data.code === 401)) {
        reject({ authExpired: true, message: '登录已失效，请重新登录' });
        return;
      }
      if (res.statusCode !== 200) {
        const message = (res.data && res.data.message) || ('请求失败：' + res.statusCode);
        const headers = res.header || {};
        const requestId = headers['X-Request-Id'] || headers['x-request-id']
          || headers['X-Cloudbase-Request-Id'] || headers['x-cloudbase-request-id'];
        reject(message + (res.statusCode >= 500 && requestId ? '（请求编号：' + requestId + '）' : ''));
        return;
      }
      if (!res.data || res.data.code !== 200) {
        reject((res.data && res.data.message) || '服务端返回异常');
        return;
      }
      resolve(res.data.data);
    };
    const handleFailure = (err) => reject('网络错误：' + ((err && err.errMsg) || '请求失败'));
    const header = Object.assign(
      { 'content-type': 'application/json' },
      requiresAuth ? getAuthorizationHeader() : {}
    );

    if (isCloudTransport()) {
      try {
        validateCloudConfig();
        wx.cloud.callContainer({
          config: { env: runtime.cloud.envId },
          path: runtime.cloud.apiPrefix + url,
          method,
          data,
          timeout: 45000,
          header: Object.assign({ 'X-WX-SERVICE': runtime.cloud.serviceName }, header),
          success: handleSuccess,
          fail: handleFailure
        });
      } catch (error) {
        reject(error.message || String(error));
      }
      return;
    }

    wx.request({
      url: runtime.localApiBaseUrl + url,
      method,
      data,
      timeout: 45000,
      header,
      success: handleSuccess,
      fail: handleFailure
    });
  });
}

/**
 * API 请求总入口；默认要求登录，遇到明确 401 时刷新登录态并且只重试一次。
 */
function request(url, method, data, options) {
  const settings = Object.assign({ requiresAuth: true, retryOnUnauthorized: true }, options || {});
  const ready = settings.requiresAuth ? ensureAuthenticated() : Promise.resolve();

  return ready
    .then(() => sendRequest(url, method, data, settings.requiresAuth))
    .catch((error) => {
      if (!error || !error.authExpired || !settings.requiresAuth || !settings.retryOnUnauthorized) {
        return Promise.reject(error && error.message ? error.message : error);
      }

      wx.removeStorageSync(AUTH_TOKEN_KEY);
      const app = getApp();
      return app.ensureLogin(true)
        .then(() => request(url, method, data, {
          requiresAuth: true,
          retryOnUnauthorized: false
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

/** 用户菜谱与统一菜谱目录相关接口。 */
const RecipeAPI = {
  getList: () => request('/recipe/list', 'GET'),
  searchCatalog: (keyword, goal, cuisine, source, page, size) => request('/recipe/catalog', 'GET', { keyword, goal, cuisine, source, page, size }),
  get: (recipeId) => request('/recipe/get/' + encodeURIComponent(recipeId), 'GET'),
  save: (recipe) => request('/recipe/save', 'POST', recipe),
  deleteByRecipeId: (recipeId) => request('/recipe/delete/' + encodeURIComponent(recipeId), 'DELETE')
};

/** 训练记录、计划完成及取消相关接口。 */
const WorkoutAPI = {
  getList: () => request('/workout/list', 'GET'),
  getByDate: (date) => request('/workout/get/' + encodeURIComponent(date), 'GET'),
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
  searchFoods: (keyword, category, page, size) => request('/database/food/search', 'GET', { keyword, category, page, size }),
  searchExercises: (keyword, category, page, size) => request('/database/exercise/search', 'GET', { keyword, category, page, size }),
  getFoodCategories: () => request('/database/food/categories', 'GET'),
  getExerciseCategories: () => request('/database/exercise/categories', 'GET'),
  resolveFoods: (names) => request('/database/food/resolve', 'POST', { names }),
  resolveExercises: (names) => request('/database/exercise/resolve', 'POST', { names })
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
  RecipeAPI,
  WorkoutAPI,
  CompareAPI,
  AnalysisAPI,
  ActivityAPI,
  PopulationComparisonAPI,
  DatabaseAPI,
  ImageAPI
};
