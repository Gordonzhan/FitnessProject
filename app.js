const { AuthAPI } = require('./utils/api');
const { initializeCloud } = require('./utils/cloud-runtime');

App({
  globalData: {
    userInfo: null,
    currentUser: null,
    loginPromise: null
  },
  /** 初始化本地兼容数据，并在小程序启动时建立服务端登录态。 */
  onLaunch() {
    try {
      initializeCloud();
    } catch (error) {
      console.error('云托管初始化失败：', error);
    }
    // 初始化本地存储
    if (!wx.getStorageSync('recipes')) {
      wx.setStorageSync('recipes', []);
    }
    if (!wx.getStorageSync('workouts')) {
      wx.setStorageSync('workouts', []);
    }
    if (!wx.getStorageSync('todayIntake')) {
      wx.setStorageSync('todayIntake', {}); // 今日饮食记录
    }
    if (!wx.getStorageSync('userProfile')) {
      // 默认用户信息
      wx.setStorageSync('userProfile', {
        gender: 'male',
        age: 25,
        height: 175,
        weight: 70,
        goal: 'maintain',
        activityLevel: 1.375
      });
    }
    if (!wx.getStorageSync('userWeight')) {
      wx.setStorageSync('userWeight', 70); // 默认体重 70kg
    }
    this.ensureLogin().catch((error) => {
      console.error('自动登录失败：', error);
    });
  },

  /**
   * 确保请求前已有有效登录态。
   * 多个页面同时触发登录时共享同一个 Promise；forceRefresh 用于 401 后重新登录。
   */
  ensureLogin(forceRefresh) {
    const token = wx.getStorageSync('authToken');
    const cachedUser = wx.getStorageSync('userProfile');
    if (!forceRefresh && token && cachedUser && cachedUser.id) {
      this.globalData.currentUser = cachedUser;
      return Promise.resolve(cachedUser);
    }
    if (this.globalData.loginPromise) {
      return this.globalData.loginPromise;
    }

    if (forceRefresh) {
      wx.removeStorageSync('authToken');
    }

    this.globalData.loginPromise = this.loginWithServer(1)
      .then((session) => {
        if (!session || !session.token || !session.user || !session.user.id) {
          return Promise.reject('服务端登录响应不完整');
        }
        wx.setStorageSync('authToken', session.token);
        wx.setStorageSync('userProfile', session.user);
        wx.setStorageSync('userWeight', Number(session.user.weight) || 70);
        this.globalData.currentUser = session.user;
        return session.user;
      })
      .finally(() => {
        this.globalData.loginPromise = null;
      });

    return this.globalData.loginPromise;
  },

  /** 微信临时 code 失效时重新获取一次；配置或网络错误不做无意义重试。 */
  loginWithServer(retries) {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (result) => {
          if (result.code) {
            resolve(result.code);
          } else {
            reject('微信登录未返回有效凭证');
          }
        },
        fail: (error) => reject('微信登录失败：' + error.errMsg)
      });
    })
      .then((code) => AuthAPI.login(code))
      .catch(error => {
        const message = error && error.message ? error.message : String(error || '');
        if (retries > 0 && /临时凭证已失效|invalid code|code been used/i.test(message)) {
          return this.loginWithServer(retries - 1);
        }
        return Promise.reject(error);
      });
  }
})
