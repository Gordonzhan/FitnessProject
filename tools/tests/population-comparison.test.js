const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.join(__dirname, '../..');

function harness(summary) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/profile/comparison/comparison.js'), 'utf8'), {
    require: () => ({ PopulationComparisonAPI: { getTrainingComparison: async () => summary } }),
    getApp: () => ({ ensureLogin: async () => ({ id: 1 }) }),
    Page: value => { definition = value; },
    wx: { stopPullDownRefresh() {} },
    Promise, Number, String, Math, console
  });
  definition.data = JSON.parse(JSON.stringify(definition.data));
  definition.setData = patch => Object.assign(definition.data, patch);
  return definition;
}

test('R7 renders only current metrics and anonymous percentile wording', async () => {
  const page = harness({
    startDate: '2026-08-10', endDate: '2026-09-08', status: 'READY',
    cohortLabel: '全部健身目标的活跃样本', sampleSize: 15, minimumSampleSize: 10,
    demoData: true,
    metrics: [{ code: 'COMPLETED_SESSIONS', label: '完成训练', currentValue: 8,
      unit: '次', percentile: 67, description: '近30天完成训练次数。' }],
    leaderboard: {
      title: '近30天完成训练次数榜', currentRank: 4, totalParticipants: 16,
      entries: [{ rank: 1, displayName: 'g****n', valueBand: '13–16 次' }],
      note: '他人次数按区间展示。'
    },
    notices: ['不提供其他用户身份、明细或精确数值。']
  });

  page._unloaded = false;
  await page.loadComparison();

  assert.equal(page.data.metrics[0].percentileText, '达到或超过 67%');
  assert.equal(page.data.metrics[0].barWidth, 67);
  assert.equal(page.data.summary.demoData, true);
  assert.equal(page.data.leaderboardEntries[0].displayName, 'g****n');
  assert.equal(page.data.leaderboardEntries[0].rowKey, '1-0');
});

test('R7 keeps percentile hidden when population is below privacy threshold', () => {
  const page = harness(null);
  page._unloaded = false;
  page.applySummary({
    startDate: '2026-08-10', endDate: '2026-09-08', status: 'POPULATION_INSUFFICIENT',
    cohortLabel: '全部活跃样本', sampleSize: 6, minimumSampleSize: 10, demoData: false,
    metrics: [{ code: 'ACTIVE_DAYS', label: '实际训练天数', currentValue: 4,
      unit: '天', percentile: null, description: '按天去重。' }], notices: []
  });

  assert.equal(page.data.metrics[0].percentileText, '暂不计算');
  assert.equal(page.data.metrics[0].barWidth, 0);
  assert.match(page.data.statusDetail, /10/);
});

test('R7 leaderboard exposes masked names without raw participant identity fields', () => {
  const files = [
    fs.readFileSync(path.join(root, 'pages/profile/comparison/comparison.wxml'), 'utf8'),
    fs.readFileSync(path.join(root, 'utils/api.js'), 'utf8')
  ].join('\n');
  assert.doesNotMatch(files, /participantKey|openid|userId/);
  assert.doesNotMatch(files, /nickname|avatar|alias/);
  assert.match(files, /displayName/);
  assert.match(files, /valueBand/);
});

test('R7 profile accepts an optional display name and submits it with the profile', () => {
  const markup = fs.readFileSync(path.join(root, 'pages/profile/profile.wxml'), 'utf8');
  const source = fs.readFileSync(path.join(root, 'pages/profile/profile.js'), 'utf8');
  assert.match(markup, /value="\{\{displayName\}\}"/);
  assert.match(markup, /bindinput="inputDisplayName"/);
  assert.match(markup, /g\*\*\*\*n/);
  assert.match(source, /displayName: String\(displayName \|\| ''\)\.trim\(\)/);
});
