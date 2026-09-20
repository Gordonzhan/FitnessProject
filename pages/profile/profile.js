const { UserAPI } = require('../../utils/api');
const { toLocalDateString } = require('../../utils/date');
const app = getApp();

Page({
  data: {
    displayName: '',
    gender: 'male',
    age: '',
    height: '',
    weight: '',
    goal: 'lose_weight',
    activityIndex: 1,
    activityLevels: [
      { label: '久坐（几乎不运动）', value: 1.2 },
      { label: '轻度活动（每周 1-3 天运动）', value: 1.375 },
      { label: '中度活动（每周 3-5 天运动）', value: 1.55 },
      { label: '高度活动（每周 6-7 天运动）', value: 1.725 },
      { label: '非常活跃（体力劳动或专业训练）', value: 1.9 }
    ],
    bmr: 0,
    dailyCalories: 0,
    goalText: ''
  },
  /** 页面加载时建立登录态并读取已有用户档案。 */
  onLoad() {
    app.ensureLogin()
      .then(() => this.loadProfile())
      .catch((error) => {
        wx.showToast({ title: '登录失败：' + error, icon: 'none' });
      });
  },
  /** 将本地用户档案映射到表单，并同步计算建议热量。 */
  loadProfile() {
    const profile = wx.getStorageSync('userProfile') || {};
    if (profile) {
      const activityIndex = this.data.activityLevels.findIndex(item => item.value === profile.activityLevel);
      this.setData({
        displayName: profile.displayName || '',
        gender: profile.gender || 'male',
        age: profile.age || '',
        height: profile.height || '',
        weight: profile.weight || '',
        goal: profile.goal || 'lose_weight',
        activityIndex: activityIndex >= 0 ? activityIndex : 1,
        activityLevel: profile.activityLevel || 1.375
      });
      this.calculateCalories();
    }
  },
  /** 更新排行榜使用的可选展示昵称；匿名快照只保存掩码结果。 */
  inputDisplayName(e) {
    this.setData({ displayName: e.detail.value });
  },
  /** 更新性别选项。 */
  selectGender(e) {
    this.setData({ gender: e.currentTarget.dataset.value });
  },
  /** 更新年龄并重新计算能量目标。 */
  inputAge(e) {
    this.setData({ age: e.detail.value });
    this.calculateCalories();
  },
  /** 更新身高并重新计算能量目标。 */
  inputHeight(e) {
    this.setData({ height: e.detail.value });
    this.calculateCalories();
  },
  /** 更新体重并重新计算能量目标。 */
  inputWeight(e) {
    this.setData({ weight: e.detail.value });
    this.calculateCalories();
  },
  /** 切换健身目标并重新计算建议摄入量。 */
  selectGoal(e) {
    this.setData({ goal: e.currentTarget.dataset.value });
    this.calculateCalories();
  },
  /** 根据选择项更新活动系数。 */
  changeActivity(e) {
    const index = e.detail.value;
    this.setData({
      activityIndex: index,
      activityLevel: this.data.activityLevels[index].value
    });
    this.calculateCalories();
  },
  /** 根据档案实时计算 BMR、TDEE 和目标热量。 */
  calculateCalories() {
    const { gender, age, height, weight, goal, activityLevel } = this.data;
    
    if (!age || !height || !weight) {
      this.setData({ bmr: 0, dailyCalories: 0, goalText: '' });
      return;
    }
    
    // 计算 BMR (Mifflin-St Jeor 公式)
    let bmr;
    if (gender === 'male') {
      bmr = 10 * weight + 6.25 * height - 5 * age + 5;
    } else {
      bmr = 10 * weight + 6.25 * height - 5 * age - 161;
    }
    
    // 计算 TDEE (每日总能量消耗)
    const tdee = bmr * (activityLevel || 1.375);
    
    // 根据目标调整
    let dailyCalories;
    let goalText;
    
    if (goal === 'lose_weight') {
      dailyCalories = tdee - 500;
      goalText = '减脂模式：每日减少 500kcal 摄入';
    } else if (goal === 'gain_muscle') {
      dailyCalories = tdee + 300;
      goalText = '增肌模式：每日增加 300kcal 摄入';
    } else {
      dailyCalories = tdee;
      goalText = '保持模式：维持当前体重';
    }
    
    this.setData({
      bmr: Math.round(bmr),
      dailyCalories: Math.round(dailyCalories),
      goalText: goalText
    });
  },
  /** 校验并保存用户档案，同时刷新登录缓存和训练计算使用的体重。 */
  saveProfile() {
    const { displayName, gender, age, height, weight, goal, activityLevel } = this.data;
    
    if (!age || !height || !weight) {
      wx.showToast({
        title: '请填写完整信息',
        icon: 'none'
      });
      return;
    }
    
    const profile = {
      displayName: String(displayName || '').trim(),
      gender,
      age: parseInt(age),
      height: parseInt(height),
      weight: parseFloat(weight),
      goal,
      activityLevel,
      weightRecordDate: toLocalDateString()
    };
    
    // 调用API更新用户信息
    UserAPI.update(profile)
      .then((updatedProfile) => {
        // 同时更新本地存储
        wx.setStorageSync('userProfile', updatedProfile);
        wx.setStorageSync('userWeight', parseFloat(weight));
        app.globalData.currentUser = updatedProfile;
        
        wx.showToast({
          title: '保存成功',
          icon: 'success'
        });
        
        setTimeout(() => {
          wx.navigateBack();
        }, 1000);
      })
      .catch((error) => {
        wx.showToast({
          title: '保存失败：' + error,
          icon: 'none'
        });
      });
  },
  /** 进入 R5 个人阶段性饮食与训练分析。 */
  goToAnalysis() {
    wx.navigateTo({ url: '/pages/profile/analysis/analysis' });
  },
  /** 进入 R6 微信运动步数与独立估算消耗。 */
  goToActivity() {
    wx.navigateTo({ url: '/pages/profile/activity/activity' });
  },
  /** 进入 R7 匿名人群训练百分位对比。 */
  goToPopulationComparison() {
    wx.navigateTo({ url: '/pages/profile/comparison/comparison' });
  }
})
