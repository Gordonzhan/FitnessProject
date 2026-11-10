const { AiAPI } = require('../../../utils/api');
const { toLocalDateString } = require('../../../utils/date');
const app = getApp();

function normalizeMessages(messages) {
  return (messages || []).map(message => {
    const citations = [];
    const seen = new Set();
    (message.citations || []).forEach(citation => {
      const key = `${citation.documentId || ''}:${citation.version || ''}`;
      if (seen.has(key)) return;
      seen.add(key);
      citations.push(Object.assign({}, citation, {
        sourceUrl: /^https:\/\//.test(String(citation.sourceUrl || '')) ? citation.sourceUrl : ''
      }));
    });
    return Object.assign({}, message, { citations });
  });
}

Page({
  data: {
    sessionId: '', messages: [], inputText: '', loading: true, sending: false, clearing: false, error: '',
    quickPrompts: ['分析我近7天的训练', '今天饮食还剩多少额度', '结合近期数据给我建议', '帮我生成今天的训练草稿']
  },

  onLoad() {
    app.ensureLogin().then(() => this.loadSession())
      .catch(error => this.setData({ loading: false, error: '登录失败：' + error }));
  },

  onPullDownRefresh() {
    this.loadSession().finally(() => wx.stopPullDownRefresh());
  },

  loadSession() {
    this.setData({ loading: true, error: '' });
    return AiAPI.openAgentSession().then(session => {
      this.setData({ sessionId: session.sessionId, messages: normalizeMessages(session.messages) });
      this.scrollToBottom();
    }).catch(error => this.setData({ error: String(error) }))
      .finally(() => this.setData({ loading: false }));
  },

  onInput(e) { this.setData({ inputText: e.detail.value }); },
  useQuickPrompt(e) { this.setData({ inputText: e.currentTarget.dataset.value || '' }); },

  sendMessage() {
    const content = String(this.data.inputText || '').trim();
    if (!content || !this.data.sessionId || this.data.sending) return;
    const previousMessages = this.data.messages.slice();
    const optimistic = previousMessages.concat({ role: 'user', content, pending: true });
    this.setData({ inputText: '', sending: true, error: '', messages: optimistic });
    this.scrollToBottom();
    AiAPI.sendAgentMessage(this.data.sessionId, content).then(session => {
      this.setData({ messages: normalizeMessages(session.messages) });
      this.scrollToBottom();
    }).catch(error => {
      this.setData({ error: String(error), inputText: content, messages: previousMessages });
      wx.showToast({ title: '发送失败，内容已保留，请稍后重试', icon: 'none' });
    }).finally(() => this.setData({ sending: false }));
  },

  clearConversation() {
    if (!this.data.sessionId || !this.data.messages.length || this.data.sending || this.data.clearing) return;
    wx.showModal({
      title: '清空当前对话',
      content: '将永久删除当前会话的全部聊天记录和草稿卡片，清空后无法恢复；已确认保存的训练或饮食不会受影响。',
      confirmText: '确认清空',
      confirmColor: '#c75b45',
      success: result => {
        if (!result.confirm) return;
        this.setData({ clearing: true, error: '' });
        AiAPI.clearAgentMessages(this.data.sessionId).then(session => {
          this.setData({ messages: normalizeMessages(session.messages), inputText: '' });
          wx.showToast({ title: '当前对话已清空', icon: 'success' });
        }).catch(error => {
          this.setData({ error: String(error) });
          wx.showToast({ title: '清空失败，请稍后重试', icon: 'none' });
        }).finally(() => this.setData({ clearing: false }));
      }
    });
  },

  openWorkoutDraft(e) {
    const index = Number(e.currentTarget.dataset.index);
    const message = this.data.messages[index];
    if (!message || !message.actionPayload) return;
    wx.setStorageSync('agentWorkoutDraftTransfer', {
      date: message.actionPayload.date || toLocalDateString(), draft: message.actionPayload,
      request: message.actionPayload.userRequest || '根据 Fitness Agent 对话生成训练'
    });
    wx.navigateTo({ url: '/pages/workout/ai-draft/ai-draft?fromAgent=1' });
  },

  copyCitationLink(e) {
    const url = String(e.currentTarget.dataset.url || '');
    if (!/^https:\/\//.test(url)) return;
    wx.setClipboardData({
      data: url,
      success: () => wx.showToast({ title: '来源链接已复制', icon: 'success' })
    });
  },

  scrollToBottom() {
    setTimeout(() => wx.pageScrollTo({ scrollTop: 999999, duration: 200 }), 50);
  }
});
