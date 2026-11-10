const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..');
const allowedProfiles = new Set(['local', 'production']);

/** 将选定档案生成成小程序唯一读取的 runtime.js，避免业务代码出现环境分支。 */
function selectRuntime(profile) {
  if (!allowedProfiles.has(profile)) {
    throw new Error('配置档案只能是 local 或 production');
  }
  const source = path.join(projectRoot, 'config', `runtime.${profile}.js`);
  const target = path.join(projectRoot, 'config', 'runtime.js');
  const generated = [
    '// 此文件由 npm run env:' + profile + ' 自动生成，请勿手工编辑。',
    fs.readFileSync(source, 'utf8').trim(),
    ''
  ].join('\n');
  fs.writeFileSync(target, generated, 'utf8');
  return target;
}

if (require.main === module) {
  try {
    const target = selectRuntime(process.argv[2]);
    console.log(`已生成 ${path.relative(projectRoot, target)}，当前档案：${process.argv[2]}`);
  } catch (error) {
    console.error('生成小程序运行配置失败：' + error.message);
    process.exitCode = 1;
  }
}

module.exports = { selectRuntime };
