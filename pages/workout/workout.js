const { WorkoutAPI } = require('../../utils/api');
const { toLocalDateString, addLocalDays, compareLocalDates } = require('../../utils/date');
const app = getApp();

Page({
  data: {
    currentDate: '',
    today: '',
    maxPlanDate: '',
    isFuture: false,
    dateContextLabel: '今天',
    currentWorkout: null,
    loadingWorkout: false,
    workoutError: '',
    updatingStatus: false,
    showBackToTop: false
  },
  /** 初始化当天日期和请求版本号。 */
  onLoad() {
    this._loadSequence = 0;
    this._navigatingToEditor = false;
    const today = toLocalDateString();
    this.setData({ currentDate: today, today, maxPlanDate: addLocalDays(today, 90) });
    this.updateDateContext();
  },
  /** 页面显示时确认登录并刷新当前日期的训练。 */
  onShow() {
    this._navigatingToEditor = false;
    const today = toLocalDateString();
    this.setData({ today, maxPlanDate: addLocalDays(today, 90) });
    this.updateDateContext();
    app.ensureLogin()
      .then(() => this.loadWorkout())
      .catch((error) => wx.showToast({ title: '登录失败：' + error, icon: 'none' }));
  },
  /** 切换到前一天。 */
  prevDay() {
    if (this.data.updatingStatus) return;
    this.changeDay(-1);
  },
  /** 切换到后一天，最多允许查看未来 90 天。 */
  nextDay() {
    if (this.data.updatingStatus) return;
    const nextDate = addLocalDays(this.data.currentDate, 1);
    if (compareLocalDates(nextDate, this.data.maxPlanDate) > 0) {
      wx.showToast({ title: '最多可安排未来90天', icon: 'none' });
      return;
    }
    this.changeDay(1);
  },
  /** 按偏移天数切换训练日期，并重新计算日期上下文和加载数据。 */
  changeDay(offset) {
    const currentDate = addLocalDays(this.data.currentDate, offset);
    this.setData({ currentDate, currentWorkout: null, workoutError: '' });
    this.updateDateContext();
    this.loadWorkout();
  },
  /** 根据当前日期生成今天、历史或未来状态及页面提示。 */
  updateDateContext() {
    const comparison = compareLocalDates(this.data.currentDate, this.data.today);
    this.setData({
      isFuture: comparison > 0,
      dateContextLabel: comparison > 0 ? '训练计划' : (comparison < 0 ? '历史记录' : '今天')
    });
  },
  /** 查询当前日期训练，并通过请求序号阻止旧响应覆盖新日期。 */
  loadWorkout() {
    const date = this.data.currentDate;
    const sequence = ++this._loadSequence;
    this.setData({ loadingWorkout: true, workoutError: '' });
    return WorkoutAPI.getByDate(date)
      .then((workout) => {
        if (sequence !== this._loadSequence || date !== this.data.currentDate) return;
        this.setData({ currentWorkout: workout ? this.normalizeWorkout(workout) : null });
      })
      .catch((error) => {
        if (sequence !== this._loadSequence || date !== this.data.currentDate) return;
        console.log('加载训练失败：', error);
        this.setData({ currentWorkout: null, workoutError: '训练数据加载失败，请点击重试' });
      })
      .finally(() => {
        if (sequence === this._loadSequence && date === this.data.currentDate) {
          this.setData({ loadingWorkout: false });
        }
      });
  },
  /** 兼容旧训练数据，并整理预计/实际热量和状态展示字段。 */
  normalizeWorkout(workout) {
    const status = workout.status || 'COMPLETED';
    const estimatedCalories = Number(workout.estimatedCalories ?? workout.totalCalories ?? 0).toFixed(1);
    const actualCalories = workout.actualCalories == null
      ? null : Number(workout.actualCalories).toFixed(1);
    return {
      ...workout,
      status,
      statusText: status === 'PLANNED' ? '计划中' : (status === 'CANCELLED' ? '已取消' : '已完成'),
      estimatedCalories,
      actualCalories,
      displayCalories: status === 'COMPLETED' ? (actualCalories ?? '0.0') : estimatedCalories
    };
  },
  /** 进入当前日期的训练新增或编辑页面。 */
  addWorkout() {
    if (this.data.loadingWorkout || this.data.updatingStatus || this._navigatingToEditor) return;
    this._navigatingToEditor = true;
    wx.navigateTo({
      url: 'add-workout/add-workout?date=' + this.data.currentDate,
      fail: () => { this._navigatingToEditor = false; }
    });
  },
  /** 仅在页面滚动跨过阈值时切换返回顶部按钮。 */
  onPageScroll(e) {
    const showBackToTop = Number(e.scrollTop || 0) > 600;
    if (showBackToTop !== this.data.showBackToTop) this.setData({ showBackToTop });
  },
  /** 平滑返回训练页面顶部。 */
  scrollToTop() {
    wx.pageScrollTo({ scrollTop: 0, duration: 250 });
  },
  /** 经确认后将到期训练计划标记完成，并把确认热量计入统计。 */
  completePlan() {
    const workout = this.data.currentWorkout;
    if (!workout || workout.status !== 'PLANNED' || this.data.isFuture || this.data.updatingStatus) return;
    wx.showModal({
      title: '确认完成训练',
      content: '将按当前动作估算的 ' + workout.estimatedCalories + ' kcal 记为实际训练消耗，并计入能量统计。',
      confirmText: '确认完成',
      confirmColor: '#159255',
      success: (result) => {
        if (!result.confirm) return;
        this.setData({ updatingStatus: true });
        WorkoutAPI.complete(workout.workoutId, { actualCalories: Number(workout.estimatedCalories) })
          .then(() => this.loadWorkout())
          .then(() => wx.showToast({ title: '训练已完成', icon: 'success' }))
          .catch((error) => wx.showToast({ title: '确认失败：' + (error.message || error), icon: 'none' }))
          .finally(() => this.setData({ updatingStatus: false }));
      }
    });
  },
  /** 经确认后取消尚未完成的训练计划。 */
  cancelPlan() {
    const workout = this.data.currentWorkout;
    if (!workout || workout.status !== 'PLANNED' || this.data.updatingStatus) return;
    wx.showModal({
      title: '取消训练计划',
      content: '取消后会保留计划记录，但不会计入实际运动消耗。',
      confirmText: '取消计划',
      confirmColor: '#d86646',
      success: (result) => {
        if (!result.confirm) return;
        this.setData({ updatingStatus: true });
        WorkoutAPI.cancel(workout.workoutId)
          .then(() => this.loadWorkout())
          .then(() => wx.showToast({ title: '计划已取消', icon: 'success' }))
          .catch((error) => wx.showToast({ title: '取消失败：' + (error.message || error), icon: 'none' }))
          .finally(() => this.setData({ updatingStatus: false }));
      }
    });
  }
});
