const app = getApp();
const { CompareAPI } = require('../../utils/api');

Page({
  data: {
    currentDate: '',
    todayIntake: 0,
    todayExpenditure: 0,
    workoutStatus: '',
    workoutEstimatedCalories: 0,
    calorieDifference: 0,
    tipText: '',
    selectedRecipes: [],
    userProfile: null,
    dailyCalories: 0,
    loadingDailyData: false,
    dailyDataError: '',
    removingIntakeId: ''
  },
  /** 初始化日期和异步请求序号。 */
  onLoad() {
    this._loadSequence = 0;
    this._unloaded = false;
    this._loadedDate = '';
    this.setData({ currentDate: new Date().toISOString().split('T')[0] });
  },
  /** 页面每次显示时确认登录，并重新读取用户档案和当日能量数据。 */
  onShow() {
    app.ensureLogin()
      .then(() => {
        if (this._unloaded) return;
        this.loadUserProfile();
        return this.calculateCalories();
      })
      .catch(error => {
        if (!this._unloaded) {
          this.setData({ dailyDataError: '登录失败，请重新进入页面' });
          wx.showToast({ title: '登录失败：' + error, icon: 'none' });
        }
      });
  },
  /** 页面卸载后使未完成请求失效，避免异步回调继续 setData。 */
  onUnload() {
    this._unloaded = true;
    this._loadSequence++;
  },
  /** 从本地登录缓存读取用户档案并计算每日建议热量。 */
  loadUserProfile() {
    const userProfile = wx.getStorageSync('userProfile') || null;
    this.setData({
      userProfile,
      dailyCalories: userProfile ? this.calculateDailyCalories(userProfile) : 2000
    });
  },
  /** 使用 Mifflin-St Jeor 公式和健身目标计算每日建议摄入热量。 */
  calculateDailyCalories(profile) {
    const { gender, age, height, weight, goal, activityLevel } = profile;
    const bmr = 10 * weight + 6.25 * height - 5 * age + (gender === 'male' ? 5 : -161);
    const tdee = bmr * activityLevel;
    return Math.round(tdee + (goal === 'lose_weight' ? -500 : (goal === 'gain_muscle' ? 300 : 0)));
  },
  /** 切换到前一天。 */
  prevDay() { this.changeDay(-1); },
  /** 切换到后一天。 */
  nextDay() { this.changeDay(1); },
  /** 按偏移天数切换统计日期并重新加载数据。 */
  changeDay(offset) {
    // 确认框及移除请求期间锁定日期，避免把上一天的记录当作当前日期删除。
    if (this.data.removingIntakeId) return;
    const date = new Date(this.data.currentDate);
    date.setDate(date.getDate() + offset);
    this.setData({ currentDate: date.toISOString().split('T')[0] });
    this.calculateCalories();
  },
  /** 跳转至菜谱页选择当天饮食。 */
  goToRecipe() {
    if (!this.data.removingIntakeId) wx.switchTab({ url: '/pages/recipe/recipe' });
  },
  /**
   * 从服务端读取指定日期的摄入、训练消耗和饮食明细。
   * 使用序号和日期双重校验，防止快速切换日期导致旧响应覆盖新页面。
   */
  calculateCalories() {
    const date = this.data.currentDate;
    const sequence = ++this._loadSequence;
    if (!this.data.userProfile) {
      this.setData({
        todayIntake: 0, todayExpenditure: 0, calorieDifference: 0,
        tipText: '请先设置用户信息', selectedRecipes: [],
        loadingDailyData: false, dailyDataError: '请先设置用户信息'
      });
      return Promise.resolve(false);
    }
    this.setData({ loadingDailyData: true, dailyDataError: '' });
    return CompareAPI.getDailyData({ date })
      .then(res => {
        if (this._unloaded || sequence !== this._loadSequence || date !== this.data.currentDate) return false;
        this._loadedDate = date;
        this.setData({
          todayIntake: Number(res.intake).toFixed(1),
          todayExpenditure: Number(res.expenditure).toFixed(1),
          workoutStatus: res.workoutStatus || '',
          workoutEstimatedCalories: Number(res.workoutEstimatedCalories || 0).toFixed(1),
          calorieDifference: Number(res.difference).toFixed(1),
          tipText: res.tipText,
          selectedRecipes: res.selectedRecipes || []
        });
        return true;
      })
      .catch(() => {
        if (!this._unloaded && sequence === this._loadSequence) {
          // 旧本地缓存不再参与兜底，防止已移除的记录重新出现或显示错误热量。
          this.setData({ dailyDataError: '饮食数据加载失败，请点击重试' });
        }
        return false;
      })
      .finally(() => {
        if (!this._unloaded && sequence === this._loadSequence) {
          this.setData({ loadingDailyData: false });
        }
      });
  },
  /** 经用户确认后删除当天一条误加的饮食记录，并重新读取统计结果。 */
  removeDailyIntake(e) {
    if (this._unloaded || this.data.loadingDailyData || this.data.dailyDataError
        || this.data.removingIntakeId || this._loadedDate !== this.data.currentDate) return;
    const intakeId = String(e.currentTarget.dataset.intakeid || '');
    const item = this.data.selectedRecipes.find(recipe => String(recipe.intakeId) === intakeId);
    if (!item || !intakeId) return;
    const date = this.data.currentDate;
    this.setData({ removingIntakeId: intakeId });
    wx.showModal({
      title: '移除饮食记录',
      content: '确定移除 ' + date + ' 的这份“' + item.mealType + '”吗？只移除这一份饮食记录，不会删除食谱库中的菜谱。',
      confirmText: '确认移除',
      confirmColor: '#d86646',
      success: async result => {
        if (!result.confirm || this._unloaded || this.data.currentDate !== date) {
          if (!this._unloaded) this.setData({ removingIntakeId: '' });
          return;
        }
        try {
          await CompareAPI.removeIntake({ date, intakeId });
          if (this._unloaded) return;
          // 后端确认成功后移除这一行；统计以重新读取的服务端结果为准。
          this.setData({
            selectedRecipes: this.data.selectedRecipes.filter(recipe => String(recipe.intakeId) !== intakeId)
          });
          const refreshed = await this.calculateCalories();
          if (!this._unloaded) wx.showToast({
            title: refreshed ? '已移除这份饮食记录' : '已移除，请重试刷新统计',
            icon: refreshed ? 'success' : 'none'
          });
        } catch (error) {
          if (!this._unloaded) {
            // 超时不能证明服务端未执行，保留现状但禁止继续操作，提示重新读取确认。
            this.setData({ dailyDataError: '移除未确认，请点击重试刷新记录' });
            wx.showToast({ title: '移除未确认：' + (error.message || error), icon: 'none' });
          }
        } finally {
          if (!this._unloaded) this.setData({ removingIntakeId: '' });
        }
      },
      fail: () => {
        if (!this._unloaded) {
          this.setData({ removingIntakeId: '' });
          wx.showToast({ title: '未能打开确认框，请重试', icon: 'none' });
        }
      }
    });
  }
});
