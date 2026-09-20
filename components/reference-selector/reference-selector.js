const { DatabaseAPI } = require('../../utils/api');

const PAGE_SIZE = 20;
const SEARCH_DELAY_MS = 300;

Component({
  properties: {
    type: { type: String, value: 'food' }
  },

  data: {
    keyword: '',
    category: '',
    categories: [],
    items: [],
    page: 0,
    total: 0,
    hasMore: false,
    loading: true,
    error: ''
  },

  lifetimes: {
    /** 组件挂载后先加载分类，再展示第一页默认数据。 */
    attached() {
      this._requestVersion = 0;
      this._attached = true;
      this.loadCategories().finally(() => {
        if (this._attached) this.search(true);
      });
    },

    /** 组件卸载时取消搜索定时器，并使尚未返回的请求结果失效。 */
    detached() {
      this._attached = false;
      clearTimeout(this._searchTimer);
      this._requestVersion += 1;
    }
  },

  methods: {
    /** 根据选择器类型加载食材或动作分类。 */
    loadCategories() {
      const api = this.properties.type === 'exercise'
        ? DatabaseAPI.getExerciseCategories
        : DatabaseAPI.getFoodCategories;
      return api()
        .then((categories) => this.setData({
          categories: Array.isArray(categories) ? categories.filter(Boolean) : []
        }))
        .catch(() => this.setData({ categories: [] }));
    },

    /** 切换分类并从第一页重新查询。 */
    selectCategory(e) {
      const category = e.currentTarget.dataset.category || '';
      if (category === this.data.category) return;
      clearTimeout(this._searchTimer);
      this._requestVersion += 1;
      this.setData({ category });
      this.search(true);
    },

    /** 接收搜索词并进行 300ms 防抖，避免每次按键都请求服务端。 */
    onKeywordInput(e) {
      this.setData({ keyword: e.detail.value });
      clearTimeout(this._searchTimer);
      this._requestVersion += 1;
      this._searchTimer = setTimeout(() => this.search(true), SEARCH_DELAY_MS);
    },

    /**
     * 分页搜索食材或动作；reset 为 true 时替换列表，否则追加下一页。
     * 请求版本号用于丢弃较早返回的过期结果。
     */
    search(reset) {
      if (this.data.loading && !reset) return;
      const page = reset ? 0 : this.data.page + 1;
      const requestVersion = ++this._requestVersion;
      this.setData({ loading: true, error: '', ...(reset ? { items: [], page: 0 } : {}) });
      const api = this.properties.type === 'exercise'
        ? DatabaseAPI.searchExercises
        : DatabaseAPI.searchFoods;

      api((this.data.keyword || '').trim(), this.data.category || '', page, PAGE_SIZE)
        .then((result) => {
          if (requestVersion !== this._requestVersion) return;
          const newItems = result && Array.isArray(result.items) ? result.items : [];
          this.setData({
            items: reset ? newItems : this.data.items.concat(newItems),
            page: Number(result.page) || 0,
            total: Number(result.total) || 0,
            hasMore: Boolean(result.hasMore)
          });
        })
        .catch((error) => {
          if (requestVersion === this._requestVersion) this.setData({ error: String(error) });
        })
        .finally(() => {
          if (requestVersion === this._requestVersion) this.setData({ loading: false });
        });
    },

    /** 滚动到底部时加载下一页，正在加载或没有更多数据时忽略。 */
    loadMore() {
      if (this.data.hasMore && !this.data.loading) this.search(false);
    },

    /** 请求失败后的手动重试入口。 */
    retry() {
      this.search(this.data.items.length === 0);
    },

    /** 将用户选中的食材或动作对象通知给父页面。 */
    selectItem(e) {
      const item = this.data.items[Number(e.currentTarget.dataset.index)];
      if (item) this.triggerEvent('select', { item });
    },

    /** 通知父页面关闭选择器。 */
    close() {
      this.triggerEvent('close');
    },

    /** 拦截弹层内触摸移动，防止底层页面跟随滚动。 */
    preventMove() {}
  }
});
