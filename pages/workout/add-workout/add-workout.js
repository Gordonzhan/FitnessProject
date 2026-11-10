const app = getApp();
const { WorkoutAPI, DatabaseAPI } = require('../../../utils/api');
const { toLocalDateString, addLocalDays, compareLocalDates } = require('../../../utils/date');

Page({
  data: {
    date: '',
    exercises: [],
    totalCalories: 0,
    estimatedDurationMinutes: null,
    calorieDurationMinutes: null,
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
    estimatingCalories: false,
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
            estimatedDurationMinutes: workout.estimatedDurationMinutes || null,
            calorieDurationMinutes: workout.estimatedDurationMinutes || null,
            unknownExercises: exercises.filter(item => item.name && !item.databaseMatched)
              .map(item => item.name),
            workoutId: workout.workoutId
          });
          this.setPageMode(editableStatus, existingStatus);
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
        return { ...exercise, calorieBasisSets: Number(exercise.sets) || null,
          databaseMatched: Boolean(reference), exerciseReference: reference || null };
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
    const exercises = [...this.data.exercises, { name: '', weight: '', sets: '', reps: '', calories: 0,
      calorieDurationSeconds: null, calorieBasisSets: null,
      databaseMatched: true, exerciseReference: null }];
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
    this.scheduleCalorieEstimate();
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
    exercises[index] = { ...exercises[index], name: selected.name, calories: 0,
      calorieDurationSeconds: null, calorieBasisSets: null,
      databaseMatched: true, exerciseReference: selected };
    this.setData({ exercises, selectorVisible: false, selectingExerciseIndex: -1 });
    this.scheduleCalorieEstimate();
  },
  /** 更新动作负重并重新估算热量。 */
  inputExerciseWeight(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].weight = value;
    this.setData({ exercises });
    this.scheduleCalorieEstimate();
  },
  /** 更新动作组数并重新估算热量。 */
  inputExerciseSets(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].sets = value;
    this.setData({ exercises });
    this.scheduleCalorieEstimate();
  },
  /** 更新每组次数并重新估算热量。 */
  inputExerciseReps(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    const exercises = [...this.data.exercises];
    exercises[index].reps = value;
    this.setData({ exercises });
    this.scheduleCalorieEstimate();
  },

  /** 输入连续变化时只提交最后一次 Java 预估，避免用小程序本地公式形成第二套口径。 */
  scheduleCalorieEstimate() {
    if (this._calorieEstimateTimer) clearTimeout(this._calorieEstimateTimer);
    this._calorieEstimateTimer = setTimeout(() => this.calculateCalories(), 250);
  },

  /** Java 使用动作库 MET、参考体重和完整计划时长返回唯一预计消耗。 */
  calculateCalories() {
    const unknownExercises = this.data.exercises.filter(item => item.name && !item.exerciseReference)
      .map(item => item.name);
    const complete = this.data.exercises.map((item, index) => ({ item, index })).filter(({ item }) =>
      item.name && item.exerciseReference && Number(item.sets) > 0 && Number(item.reps) > 0);
    if (unknownExercises.length || !complete.length) {
      this.setData({ unknownExercises: [...new Set(unknownExercises)],
        totalCalories: complete.length ? this.data.totalCalories : '0.0' });
      return Promise.resolve();
    }
    const sequence = (this._calorieEstimateSequence || 0) + 1;
    this._calorieEstimateSequence = sequence;
    this.setData({ estimatingCalories: true, unknownExercises: [] });
    const payload = {
      workoutId: this.data.workoutId || null,
      estimatedDurationMinutes: this.data.calorieDurationMinutes || null,
      exercises: complete.map(({ item }) => ({
        name: item.name.trim(), weight: item.weight === '' ? null : Number(item.weight),
        sets: Number(item.sets), reps: Number(item.reps),
        calorieDurationSeconds: item.calorieDurationSeconds || null,
        calorieBasisSets: item.calorieBasisSets || null
      }))
    };
    return WorkoutAPI.estimate(payload).then((estimate) => {
      if (sequence !== this._calorieEstimateSequence) return;
      const exercises = [...this.data.exercises];
      complete.forEach(({ index }, position) => {
        exercises[index] = { ...exercises[index], calories: estimate.exercises[position].calories,
          calorieDurationSeconds: estimate.exercises[position].calorieDurationSeconds,
          calorieBasisSets: Number(exercises[index].sets) };
      });
      this.setData({
        exercises,
        totalCalories: Number(estimate.estimatedCalories ?? estimate.totalCalories ?? 0).toFixed(1),
        estimatedDurationMinutes: estimate.estimatedDurationMinutes
      });
    }).catch((error) => {
      if (sequence === this._calorieEstimateSequence) {
        wx.showToast({ title: '热量估算失败：' + error, icon: 'none' });
      }
    }).finally(() => {
      if (sequence === this._calorieEstimateSequence) this.setData({ estimatingCalories: false });
    });
  },
  /** 校验动作完整性和基础库匹配情况，然后保存训练计划或完成记录。 */
  saveWorkout() {
    if (this.data.saving || this.data.loadingWorkout || this.data.loadFailed || this.data.estimatingCalories) {
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

    const { date, exercises, totalCalories, workoutId, status, estimatedDurationMinutes } = this.data;
    const workout = {
      date: date,
      workoutId: workoutId || String(Date.now()),
      status,
      estimatedDurationMinutes,
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
