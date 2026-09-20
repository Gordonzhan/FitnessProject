const app = getApp();
const { WorkoutAPI, DatabaseAPI } = require('../../../utils/api');
const { toLocalDateString, addLocalDays, compareLocalDates } = require('../../../utils/date');

Page({
  data: {
    date: '',
    exercises: [],
    totalCalories: 0,
    workoutId: '',
    status: 'COMPLETED',
    isFuture: false,
    isHistorical: false,
    isToday: true,
    existingStatus: '',
    canChooseStatus: true,
    formTitle: '记录训练表现',
    modeHint: '保存后会作为已完成训练计入当天能量统计',
    saveText: '保存训练记录',
    unknownExercises: [],
    selectorVisible: false,
    selectingExerciseIndex: -1,
    loadingWorkout: false,
    loadFailed: false,
    saving: false,
    keyboardOpen: false,
    showBackToTop: false
  },
  /** 根据目标日期初始化训练状态，并加载该日期已有记录。 */
  onLoad(options) {
    const today = toLocalDateString();
    const date = options.date || today;
    if (compareLocalDates(date, addLocalDays(today, 90)) > 0) {
      wx.showToast({ title: '训练计划最多可安排未来90天', icon: 'none' });
      return;
    }
    const status = compareLocalDates(date, today) > 0 ? 'PLANNED' : 'COMPLETED';
    this.setData({ date });
    this.setPageMode(status);
    app.ensureLogin()
      .then(() => {
        this.loadWorkout();
      })
      .catch((error) => {
        wx.showToast({ title: '登录失败：' + error, icon: 'none' });
      });
  },
  /** 加载指定日期训练，并批量恢复动作库引用。 */
  loadWorkout() {
    this.setData({ loadingWorkout: true, loadFailed: false });
    // 调用API获取指定日期的训练
    WorkoutAPI.getByDate(this.data.date)
      .then((workout) => {
        if (!workout) return null;
        return this.resolveExerciseReferences(workout.exercises || [])
          .then((exercises) => ({ workout, exercises }));
      })
      .then((resolved) => {
        if (!resolved) return;
        const { workout, exercises } = resolved;
        if (workout) {
          const today = toLocalDateString();
          const existingStatus = workout.status || 'COMPLETED';
          const dateComparison = compareLocalDates(this.data.date, today);
          const editableStatus = existingStatus === 'COMPLETED'
            ? 'COMPLETED'
            : (existingStatus === 'PLANNED' && dateComparison >= 0
              ? 'PLANNED'
              : (dateComparison > 0 ? 'PLANNED' : 'COMPLETED'));
          this.setData({
            exercises,
            totalCalories: workout.estimatedCalories ?? workout.actualCalories ?? workout.totalCalories ?? 0,
            workoutId: workout.workoutId
          });
          this.setPageMode(editableStatus, existingStatus);
          this.calculateCalories();
        }
      })
      .catch((error) => {
        this.setData({ loadFailed: true });
        wx.showToast({ title: '训练加载失败：' + error, icon: 'none' });
      })
      .finally(() => this.setData({ loadingWorkout: false }));
  },
  /** 批量匹配动作库引用，识别历史记录中已经失效的动作名称。 */
  resolveExerciseReferences(exercises) {
    const names = exercises.map(item => item.name).filter(Boolean);
    if (!names.length) return Promise.resolve(exercises);
    return DatabaseAPI.resolveExercises(names).then((items) => {
      const references = new Map((items || []).map(item => [item.name, item]));
      return exercises.map(exercise => {
        const reference = references.get((exercise.name || '').trim());
        return { ...exercise, databaseMatched: Boolean(reference), exerciseReference: reference || null };
      });
    });
  },
  /** 根据日期和现有状态配置计划、完成或历史补录的页面文案与权限。 */
  setPageMode(status, existingStatus = '') {
    const today = toLocalDateString();
    const comparison = compareLocalDates(this.data.date, today);
    const isFuture = comparison > 0;
    const isHistorical = comparison < 0;
    const isToday = comparison === 0;
    let formTitle = isFuture ? '安排训练计划' : (isHistorical ? '补录训练记录' : '记录训练表现');
    if (existingStatus === 'PLANNED' && isFuture) formTitle = '编辑训练计划';
    if (existingStatus === 'PLANNED' && !isFuture) formTitle = '完成训练计划';
    if (existingStatus === 'CANCELLED') formTitle = isFuture ? '重新安排训练' : '记录实际训练';
    this.setData({
      status,
      isFuture,
      isHistorical,
      isToday,
      existingStatus,
      canChooseStatus: isToday && existingStatus !== 'COMPLETED',
      formTitle,
      modeHint: status === 'PLANNED'
        ? '仅保存预计消耗，到训练日确认完成后才计入能量统计'
        : (isHistorical ? '这是历史补录，保存后会计入该日期的实际消耗' : '保存后会作为已完成训练计入当天能量统计'),
      saveText: status === 'PLANNED' ? '保存训练计划' : (isHistorical ? '保存历史训练' : '保存训练记录')
    });
    if (wx.setNavigationBarTitle) wx.setNavigationBarTitle({ title: formTitle });
  },
  /** 在当天允许的范围内切换“待完成计划”和“已完成记录”。 */
  selectStatus(e) {
    if (!this.data.canChooseStatus || this.data.saving) return;
    const status = e.currentTarget.dataset.status;
    if (status !== 'PLANNED' && status !== 'COMPLETED') return;
    this.setPageMode(status, this.data.existingStatus);
  },
  /** 根据软键盘高度隐藏或恢复底部操作栏，防止遮挡动作输入。 */
  onKeyboardHeightChange(e) {
    const keyboardOpen = Number(e.detail && e.detail.height || 0) > 0;
    if (keyboardOpen !== this.data.keyboardOpen) this.setData({ keyboardOpen });
  },
  /** 仅在滚动跨过阈值时切换返回顶部入口。 */
  onPageScroll(e) {
    const showBackToTop = Number(e.scrollTop || 0) > 600;
    if (showBackToTop !== this.data.showBackToTop) this.setData({ showBackToTop });
  },
  /** 平滑返回训练表单顶部。 */
  scrollToTop() {
    wx.pageScrollTo({ scrollTop: 0, duration: 250 });
  },
  /** 在训练表单末尾新增一行空动作。 */
  addExercise() {
    this.appendExercise(false);
  },
  /** 从固定操作栏新增动作，并直接打开动作选择器。 */
  quickAddExercise() {
    this.appendExercise(true);
  },
  /** 创建动作行，可选择立即进入基础动作搜索。 */
  appendExercise(openSelector) {
    if (this.data.saving || this.data.loadingWorkout || this.data.loadFailed || this.data.selectorVisible) {
      wx.showToast({ title: this.data.loadFailed ? '训练加载失败，请返回重试' : '请等待当前操作完成', icon: 'none' });
      return;
    }
    const index = this.data.exercises.length;
    const exercises = [...this.data.exercises, { name: '', weight: '', sets: '', reps: '', calories: 0, databaseMatched: true, exerciseReference: null }];
    const update = { exercises };
    if (openSelector) {
      update.selectorVisible = true;
      update.selectingExerciseIndex = index;
    }
    this.setData(update, () => {
      if (wx.pageScrollTo) wx.pageScrollTo({ selector: '#exercise-' + index, duration: 250 });
    });
  },
  /** 删除指定动作行并重新估算总热量。 */
  removeExercise(e) {
    const index = e.currentTarget.dataset.index;
    const exercises = this.data.exercises.filter((_, i) => i !== index);
    this.setData({ exercises });
    this.calculateCalories();
  },
  /** 为指定动作行打开分页搜索选择器。 */
  openExerciseSelector(e) {
    this.setData({ selectorVisible: true, selectingExerciseIndex: Number(e.currentTarget.dataset.index) });
  },
  /** 关闭动作选择器并清除当前行索引。 */
  closeExerciseSelector() {
    this.setData({ selectorVisible: false, selectingExerciseIndex: -1 });
  },
  /** 将动作库选项及其 MET 数据写入当前动作行。 */
  selectExercise(e) {
    const index = this.data.selectingExerciseIndex;
    const selected = e.detail.item;
    if (index < 0 || !selected) return;
    const exercises = [...this.data.exercises];
    exercises[index] = { ...exercises[index], name: selected.name, databaseMatched: true, exerciseReference: selected };
    this.setData({ exercises, selectorVisible: false, selectingExerciseIndex: -1 });
    this.calculateCalories();
  },
  /** 更新动作负重并重新估算热量。 */
  inputExerciseWeight(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].weight = value;
    this.setData({ exercises });
    this.calculateCalories();
  },
  /** 更新动作组数并重新估算热量。 */
  inputExerciseSets(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].sets = value;
    this.setData({ exercises });
    this.calculateCalories();
  },
  /** 更新每组次数并重新估算热量。 */
  inputExerciseReps(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].reps = value;
    this.setData({ exercises });
    this.calculateCalories();
  },
  /** 使用动作 MET、用户体重和估算时长汇总训练消耗。 */
  calculateCalories() {
    const userWeight = wx.getStorageSync('userWeight') || 70;
    let total = 0;

    const unknownExercises = [];
    const exercises = this.data.exercises.map(exercise => {
      const name = (exercise.name || '').trim();
      const exerciseData = exercise.exerciseReference;
      const calculated = { ...exercise, databaseMatched: !name || Boolean(exerciseData), calories: 0 };
      if (name && !exerciseData) unknownExercises.push(name);
      if (exercise.name && exercise.sets && exercise.reps) {
        if (!exerciseData) return calculated;
        const met = exerciseData.met;
        const sets = parseInt(exercise.sets);
        // 估算每组30秒，组间休息60秒
        const durationHours = (sets * (30 + 60)) / 3600;
        const calories = met * userWeight * durationHours;
        calculated.calories = calories.toFixed(1);
        total += calories;
      }
      return calculated;
    });

    this.setData({
      exercises,
      unknownExercises: [...new Set(unknownExercises)],
      totalCalories: total.toFixed(1)
    });
  },
  /** 校验动作完整性和基础库匹配情况，然后保存训练计划或完成记录。 */
  saveWorkout() {
    if (this.data.saving || this.data.loadingWorkout || this.data.loadFailed) {
      wx.showToast({ title: this.data.loadFailed ? '训练加载失败，请返回重试' : '请等待训练记录加载完成', icon: 'none' });
      return;
    }
    // 验证：至少需要有一个完整的动作记录
    const validExercises = this.data.exercises.filter(item => item.name && item.weight && item.sets && item.reps);
    if (validExercises.length === 0) {
      wx.showToast({
        title: '请至少填写一个完整的动作',
        icon: 'none'
      });
      return;
    }
    if (this.data.unknownExercises.length) {
      wx.showToast({ title: '请选择基础动作：' + this.data.unknownExercises[0], icon: 'none' });
      return;
    }

    const { date, exercises, totalCalories, workoutId, status } = this.data;
    const workout = {
      date: date,
      workoutId: workoutId || String(Date.now()),
      status,
      exercises: validExercises.map(({ exerciseReference, databaseMatched, ...item }) => item),
      totalCalories: parseFloat(totalCalories)
    };

    // 调用API保存训练
    this.setData({ saving: true });
    WorkoutAPI.save(workout)
      .then(() => {
        wx.showToast({ title: status === 'PLANNED' ? '计划已保存' : '训练已记录' });
        setTimeout(() => {
          wx.navigateBack();
        }, 1000);
      })
      .catch((error) => {
        this.setData({ saving: false });
        wx.showToast({
          title: '保存失败：' + error,
          icon: 'none'
        });
      });
  }
})
