const app = getApp();
const { ActivityAPI } = require('../../../utils/api');

Page({
  data: {
    loading: true,
    syncing: false,
    deleting: false,
    error: '',
    permissionDenied: false,
    summary: null,
    todaySteps: 0,
    todayCalories: '0.0',
    sevenDayAverage: 0,
    lastSyncedText: '尚未同步',
    displayPoints: []
  },

  onLoad() {
    this._unloaded = false;
    app.ensureLogin()
      .then(() => this.loadSummary())
      .catch(error => this.setSafe({ loading: false, error: '登录失败：' + this.message(error) }));
  },

  onUnload() { this._unloaded = true; },

  onPullDownRefresh() {
    this.loadSummary().finally(() => wx.stopPullDownRefresh());
  },

  loadSummary() {
    this.setSafe({ loading: true, error: '' });
    return ActivityAPI.getWechatSummary()
      .then(summary => this.applySummary(summary))
      .catch(error => this.setSafe({ error: '活动数据加载失败：' + this.message(error) }))
      .finally(() => this.setSafe({ loading: false }));
  },

  /** 必须由用户点击触发；云托管优先用短期 cloudID，传统密文仅作非云回退。 */
  syncWechat() {
    if (this.data.syncing || this.data.deleting) return;
    this.setSafe({ syncing: true, error: '', permissionDenied: false });
    return this.loginCode()
      .then(code => this.weRunData().then(data => ({ code, data })))
      .then(result => ActivityAPI.syncWechat({
        code: result.code,
        cloudId: result.data.cloudID || '',
        encryptedData: result.data.encryptedData || '',
        iv: result.data.iv || ''
      }))
      .then(summary => {
        this.applySummary(summary);
        wx.showToast({ title: '微信步数已同步', icon: 'success' });
      })
      .catch(error => {
        const message = this.message(error);
        const denied = /auth deny|authorize|授权|permission/i.test(message);
        this.setSafe({ permissionDenied: denied, error: denied ? '你没有授权微信运动，可在微信设置中重新开启。' : '同步失败：' + message });
      })
      .finally(() => this.setSafe({ syncing: false }));
  },

  loginCode() {
    return new Promise((resolve, reject) => wx.login({
      success: result => result.code ? resolve(result.code) : reject('微信登录未返回同步凭证'),
      fail: error => reject(error && error.errMsg ? error.errMsg : '微信登录失败')
    }));
  },

  weRunData() {
    return new Promise((resolve, reject) => wx.getWeRunData({
      success: result => result.cloudID || (result.encryptedData && result.iv)
        ? resolve(result)
        : reject('微信运动未返回有效数据'),
      fail: error => reject(error && error.errMsg ? error.errMsg : '微信运动授权失败')
    }));
  },

  openPermissionSettings() {
    wx.openSetting({
      success: result => this.setSafe({ permissionDenied: result.authSetting['scope.werun'] !== true }),
      fail: () => wx.showToast({ title: '无法打开微信权限设置', icon: 'none' })
    });
  },

  deleteWechatData() {
    if (this.data.syncing || this.data.deleting || !this.data.summary || !this.data.summary.recordedDays) return;
    wx.showModal({
      title: '删除微信运动数据',
      content: '将删除本项目保存的全部微信步数和估算值，不影响微信运动中的原始数据，也不会自动撤销微信授权。',
      confirmText: '确认删除',
      confirmColor: '#d86646',
      success: result => {
        if (!result.confirm || this._unloaded) return;
        this.setSafe({ deleting: true, error: '' });
        ActivityAPI.deleteWechatData()
          .then(() => this.loadSummary())
          .then(() => wx.showToast({ title: '同步数据已删除', icon: 'success' }))
          .catch(error => this.setSafe({ error: '删除失败：' + this.message(error) }))
          .finally(() => this.setSafe({ deleting: false }));
      }
    });
  },

  applySummary(summary) {
    if (this._unloaded) return;
    const points = (summary.dailyPoints || []).map(item => ({
      ...item,
      caloriesText: Number(item.estimatedCalories || 0).toFixed(1),
      shortDate: String(item.date).slice(5)
    }));
    const maxSteps = Math.max(1, ...points.map(item => Number(item.steps || 0)));
    const displayPoints = points.slice().reverse().map(item => ({
      ...item,
      barWidth: Math.max(2, Math.round(Number(item.steps || 0) * 100 / maxSteps))
    }));
    const endDate = summary.endDate;
    const today = points.find(item => item.date === endDate) || { steps: 0, estimatedCalories: 0 };
    const sevenStart = this.shiftDate(endDate, -6);
    const recent = points.filter(item => item.date >= sevenStart && item.date <= endDate);
    const sevenDayAverage = Math.round(recent.reduce((sum, item) => sum + Number(item.steps || 0), 0) / 7);
    this.setData({
      summary,
      displayPoints,
      todaySteps: Number(today.steps || 0),
      todayCalories: Number(today.estimatedCalories || 0).toFixed(1),
      sevenDayAverage,
      lastSyncedText: this.formatTime(summary.lastSyncedAt)
    });
  },

  shiftDate(value, offset) {
    const parts = String(value).split('-').map(Number);
    const date = new Date(parts[0], parts[1] - 1, parts[2], 12);
    date.setDate(date.getDate() + offset);
    const pad = number => String(number).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  },

  formatTime(value) {
    if (!value) return '尚未同步';
    return String(value).replace('T', ' ').slice(0, 16);
  },

  message(error) { return error && error.message ? error.message : String(error || '未知错误'); },
  setSafe(update) { if (!this._unloaded) this.setData(update); }
});
