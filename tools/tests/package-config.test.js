const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.join(__dirname, '../..');

test('preview package excludes repository-only assets and enables component lazy loading', () => {
  const project = JSON.parse(fs.readFileSync(path.join(projectRoot, 'project.config.json'), 'utf8'));
  const app = JSON.parse(fs.readFileSync(path.join(projectRoot, 'app.json'), 'utf8'));
  const ignored = new Set(project.packOptions.ignore.map(item => `${item.type}:${item.value}`));

  assert.equal(app.lazyCodeLoading, 'requiredComponents');
  assert.ok(ignored.has('folder:backend'));
  assert.ok(ignored.has('folder:tools'));
  assert.ok(ignored.has('folder:images/system-recipes'));
  assert.ok(ignored.has('file:ITEM10_CLOUD_HOSTING_ROLLOUT_PLAN.md'));
  assert.ok(ignored.has('suffix:.log'));
});
