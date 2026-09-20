const { RecipeAPI, CompareAPI } = require('../../utils/api');
const app = getApp();

Page({
  data: {
    recipes: [],
    keyword: '',
    goal: '',
    cuisine: '',
    source: '',
    filterOpen: false,
    draftGoal: '',
    draftCuisine: '',
    advancedFilterCount: 0,
    total: 0,
    page: 0,
    hasMore: false,
    loading: false,
    today: new Date().toISOString().split('T')[0],
    addingRecipeId: '',
    searchFocused: false,
    showBackToTop: false
  },
  /** 初始化添加饮食的幂等请求缓存和搜索版本号。 */
  onLoad() {
    this._pendingAddRequestIds = Object.create(null);
    this._searchVersion = 0;
    this._navigatingToEditor = false;
  },
  /** 页面显示时确认登录并刷新菜谱目录。 */
  onShow() {
    this._navigatingToEditor = false;
    app.ensureLogin()
      .then(() => this.loadRecipes())
      .catch((error) => {
        wx.showToast({ title: '登录失败：' + error, icon: 'none' });
      });
  },
  /**
   * 按当前搜索和筛选条件分页加载菜谱。
   * append 为 true 时加载下一页，请求版本号用于丢弃过期搜索结果。
   */
  loadRecipes(append) {
    if (this.data.loading && append) return;
    const page = append ? this.data.page + 1 : 0;
    const version = ++this._searchVersion;
    this.setData({ loading: true });
    RecipeAPI.searchCatalog(this.data.keyword, this.data.goal, this.data.cuisine, this.data.source, page, 20)
      .then((result) => {
        if (version !== this._searchVersion) return;
        const incoming = (result.items || []).map(recipe => Object.assign({}, recipe, { coverFailed: false }));
        this.setData({
          recipes: append ? this.data.recipes.concat(incoming) : incoming,
          total: result.total || 0,
          page: result.page || 0,
          hasMore: Boolean(result.hasMore)
        });
      })
      .catch((error) => {
        wx.showToast({
          title: '加载失败：' + error,
          icon: 'none'
        });
      })
      .finally(() => {
        if (version === this._searchVersion) this.setData({ loading: false });
      });
  },
  /** 进入新增用户菜谱页面。 */
  addRecipe() {
    if (this._navigatingToEditor) return;
    this._navigatingToEditor = true;
    wx.navigateTo({
      url: 'add-recipe/add-recipe',
      fail: () => { this._navigatingToEditor = false; }
    });
  },
  /** 搜索框获得焦点时隐藏固定按钮，避免与软键盘争夺空间。 */
  onSearchFocus() {
    this.setData({ searchFocused: true });
  },
  /** 搜索框失去焦点后恢复固定快捷入口。 */
  onSearchBlur() {
    this.setData({ searchFocused: false });
  },
  /** 仅在跨过阈值时更新返回顶部按钮，避免滚动期间频繁 setData。 */
  onPageScroll(e) {
    const showBackToTop = Number(e.scrollTop || 0) > 600;
    if (showBackToTop !== this.data.showBackToTop) this.setData({ showBackToTop });
  },
  /** 平滑返回菜谱列表顶部。 */
  scrollToTop() {
    wx.pageScrollTo({ scrollTop: 0, duration: 250 });
  },
  /** 标记封面加载失败，使界面显示占位图而不删除服务端图片记录。 */
  onCoverError(e) {
    const recipeId = e.currentTarget.dataset.recipeid;
    const index = this.data.recipes.findIndex(recipe => recipe.recipeId === recipeId);
    if (index >= 0) this.setData({ ['recipes[' + index + '].coverFailed']: true });
  },
  /** 打开用户菜谱编辑页或系统菜谱只读详情页。 */
  viewRecipe(e) {
    const recipeId = e.currentTarget.dataset.recipeid;
    const sourceType = e.currentTarget.dataset.sourcetype;
    wx.navigateTo({
      url: sourceType === 'SYSTEM'
        ? `add-recipe/add-recipe?templateId=${recipeId}`
        : `add-recipe/add-recipe?recipeId=${recipeId}`
    });
  },
  /** 以系统菜谱为模板进入复制创建流程。 */
  copyRecipe(e) {
    wx.navigateTo({ url: `add-recipe/add-recipe?templateId=${e.currentTarget.dataset.recipeid}` });
  },
  /** 从今日饮食中移除该菜谱产生的记录。 */
  cancelSelect(e) {
    const recipeId = e.currentTarget.dataset.recipeid;
    const mealType = e.currentTarget.dataset.mealtype;
    const today = new Date().toISOString().split('T')[0];
    
    wx.showModal({
      title: '确认取消',
      content: `确定要从今日饮食中移除"${mealType}"吗？`,
      success: (res) => {
        if (res.confirm) {
          // 调用API从今日饮食中移除
          CompareAPI.removeRecipe({
            date: today,
            recipeId: recipeId
          })
            .then(() => {
              wx.showToast({
                title: '已取消选择',
                icon: 'success'
              });
              
              // 重新加载数据
              this.loadRecipes();
            })
            .catch((error) => {
              wx.showToast({
                title: '操作失败：' + error,
                icon: 'none'
              });
            });
        }
      }
    });
  },
  /** 确认后删除用户自建菜谱；系统菜谱不提供此操作。 */
  deleteRecipe(e) {
    const recipeId = e.currentTarget.dataset.recipeid;
    const mealType = e.currentTarget.dataset.mealtype;
    
    wx.showModal({
      title: '确认删除',
      content: `确定要永久删除"${mealType}"吗？\n\n删除后该菜谱将从食谱库中消失，但不会删除历史记录。`,
      success: (res) => {
        if (res.confirm) {
          // 调用API删除菜谱
          RecipeAPI.deleteByRecipeId(recipeId)
            .then(() => {
              wx.showToast({
                title: '删除成功',
                icon: 'success'
              });
              
              // 重新加载数据
              this.loadRecipes();
            })
            .catch((error) => {
              wx.showToast({
                title: '删除失败：' + error,
                icon: 'none'
              });
            });
        }
      }
    });
  },
  /**
   * 将菜谱加入今日饮食。
   * 同一次未完成操作复用 requestId，防止网络重试产生重复记录。
   */
  selectForToday(e) {
    const recipeId = e.currentTarget.dataset.recipeid;
    const mealType = e.currentTarget.dataset.mealtype;
    const today = new Date().toISOString().split('T')[0];
    if (!recipeId || this.data.addingRecipeId) return;
    // 失败后再次点击沿用同一个 requestId：即使第一次其实已被服务端接收，也不会重复添加。
    const requestId = this._pendingAddRequestIds[recipeId]
      || `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
    this._pendingAddRequestIds[recipeId] = requestId;
    this.setData({ addingRecipeId: recipeId });
    
    // 调用API添加到今日饮食
    CompareAPI.addRecipe({
      date: today,
      recipeId: recipeId,
      requestId
    })
      .then(() => {
        delete this._pendingAddRequestIds[recipeId];
        wx.showToast({
          title: '已添加到今日饮食',
          icon: 'success'
        });
      })
      .catch((error) => {
        wx.showToast({
          title: '添加结果未确认，可再次点击重试：' + error,
          icon: 'none'
        });
      })
      .finally(() => this.setData({ addingRecipeId: '' }));
  },
  /** 搜索输入防抖后从第一页刷新菜谱。 */
  searchRecipe(e) {
    const keyword = (e.detail.value || '').trim();
    this.setData({ keyword });
    clearTimeout(this._searchTimer);
    this._searchTimer = setTimeout(() => this.loadRecipes(false), 300);
  },
  /** 清空关键字并恢复默认菜谱列表。 */
  clearSearch() {
    clearTimeout(this._searchTimer);
    this.setData({ keyword: '' });
    this.loadRecipes(false);
  },
  /** 直接切换健身目标筛选并刷新列表。 */
  selectGoal(e) {
    const goal = e.currentTarget.dataset.goal || '';
    if (goal === this.data.goal) return;
    this.setData({ goal });
    this.loadRecipes(false);
  },
  /** 直接切换菜系筛选并刷新列表。 */
  selectCuisine(e) {
    const cuisine = e.currentTarget.dataset.cuisine || '';
    if (cuisine === this.data.cuisine) return;
    this.setData({ cuisine });
    this.loadRecipes(false);
  },
  /** 切换菜谱来源；选择我的菜谱时同步清理不适用的系统筛选条件。 */
  selectSource(e) {
    const source = e.currentTarget.dataset.source || '';
    if (source === this.data.source) return;
    const update = { source };
    // 个人菜谱目前没有系统模板的菜式和目标标签，切换时清除不适用条件。
    if (source === 'USER' || source === '') {
      update.goal = '';
      update.cuisine = '';
      update.advancedFilterCount = 0;
    }
    this.setData(update);
    this.loadRecipes(false);
  },
  /** 打开高级筛选弹层，并复制当前条件作为草稿。 */
  openFilters() {
    if (this.data.source === 'USER') return;
    this.setData({
      filterOpen: true,
      draftGoal: this.data.goal,
      draftCuisine: this.data.cuisine
    });
  },
  /** 关闭高级筛选弹层，不应用草稿修改。 */
  closeFilters() {
    this.setData({ filterOpen: false });
  },
  /** 阻止点击筛选面板内部时触发遮罩关闭。 */
  stopPropagation() {},
  /** 更新高级筛选中的健身目标草稿。 */
  selectDraftGoal(e) {
    this.setData({ draftGoal: e.currentTarget.dataset.goal || '' });
  },
  /** 更新高级筛选中的菜系草稿。 */
  selectDraftCuisine(e) {
    this.setData({ draftCuisine: e.currentTarget.dataset.cuisine || '' });
  },
  /** 清空尚未应用的高级筛选草稿。 */
  resetDraftFilters() {
    this.setData({ draftGoal: '', draftCuisine: '' });
  },
  /** 应用高级筛选草稿并从第一页刷新目录。 */
  applyFilters() {
    const goal = this.data.draftGoal || '';
    const cuisine = this.data.draftCuisine || '';
    const advancedFilterCount = Number(Boolean(goal)) + Number(Boolean(cuisine));
    this.setData({
      goal,
      cuisine,
      source: advancedFilterCount ? 'SYSTEM' : this.data.source,
      advancedFilterCount,
      filterOpen: false
    });
    this.loadRecipes(false);
  },
  /** 单独移除已应用的健身目标筛选。 */
  clearGoalFilter() {
    if (!this.data.goal) return;
    const advancedFilterCount = Number(Boolean(this.data.cuisine));
    this.setData({ goal: '', advancedFilterCount });
    this.loadRecipes(false);
  },
  /** 单独移除已应用的菜系筛选。 */
  clearCuisineFilter() {
    if (!this.data.cuisine) return;
    const advancedFilterCount = Number(Boolean(this.data.goal));
    this.setData({ cuisine: '', advancedFilterCount });
    this.loadRecipes(false);
  },
  /** 快速切换到系统菜谱列表。 */
  showSystemRecipes() {
    this.setData({ source: 'SYSTEM', goal: '', cuisine: '', advancedFilterCount: 0 });
    this.loadRecipes(false);
  },
  /** 页面触底时按需加载下一页。 */
  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadRecipes(true);
  },
  /** 页面卸载时取消搜索定时器并使未完成请求失效。 */
  onUnload() {
    clearTimeout(this._searchTimer);
  }
})
