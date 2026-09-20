const fs = require('fs');
const path = require('path');

const VERSION = 'foundation-2026-04';
const mappingPath = path.join(__dirname, 'reference-data', 'fooddata-central-foundation-map.json');
const sourcePath = process.argv[2];
const outputPath = process.argv[3];

if (!sourcePath || !outputPath) {
  console.error('用法: node tools/generate-reference-food-sql.js <USDA Foundation JSON> <输出 SQL>');
  process.exit(1);
}

const source = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const mappings = JSON.parse(fs.readFileSync(mappingPath, 'utf8'));
const foods = new Map((source.FoundationFoods || [])
  .filter((item) => item && item.fdcId)
  .map((item) => [Number(item.fdcId), item]));

function escapeSql(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function nutrient(food, numbers, defaultValue) {
  const candidates = Array.isArray(numbers) ? numbers : [numbers];
  const found = candidates.map((number) => (food.foodNutrients || [])
    .find((item) => String(item.nutrient && item.nutrient.number) === number)).find(Boolean);
  if ((!found || !Number.isFinite(Number(found.amount))) && defaultValue !== undefined) {
    return Number(defaultValue).toFixed(2);
  }
  if (!found || !Number.isFinite(Number(found.amount))) {
    throw new Error(`FDC ${food.fdcId} 缺少营养素 ${candidates.join('/')}`);
  }
  return Number(found.amount).toFixed(2);
}

function energy(food) {
  try {
    return nutrient(food, ['208', '958', '957']);
  } catch (error) {
    const protein = Number(nutrient(food, '203', 0));
    const carb = Number(nutrient(food, '205', 0));
    const fat = Number(nutrient(food, '204', 0));
    return (protein * 4 + carb * 4 + fat * 9).toFixed(2);
  }
}

const values = mappings.map((mapping) => {
  const food = foods.get(Number(mapping.fdcId));
  if (!food) throw new Error(`Foundation 数据中不存在 FDC ${mapping.fdcId}`);
  return `(${[
    escapeSql(mapping.foodName), escapeSql(mapping.aliases || ''), escapeSql(mapping.category),
    escapeSql(mapping.servingState), nutrient(food, '203', 0), nutrient(food, '205', 0), nutrient(food, '204', 0),
    energy(food), escapeSql('USDA_FDC'), escapeSql(`FDC:${mapping.fdcId}`),
    escapeSql(VERSION), 1, 70
  ].join(',')})`;
});

const existingOutput = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, 'utf8') : '';
const exerciseMarker = '\n-- 训练活动的 MET 与活动编码来自 2024 Adult Compendium。';
const exerciseStart = existingOutput.indexOf(exerciseMarker);
const exerciseSection = exerciseStart >= 0 ? existingOutput.slice(exerciseStart) : '';

const sql = `-- 食材段由 tools/generate-reference-food-sql.js 生成。营养值按每 100g 可食部分计。\n` +
  `-- 来源：USDA FoodData Central Foundation Foods (${VERSION})；中文名称与分类由项目维护。\n` +
  `USE \`fitness_diary\`;\n\n` +
  `INSERT INTO \`food_database\`\n` +
  `  (\`food_name\`,\`aliases\`,\`category\`,\`serving_state\`,\`protein\`,\`carb\`,\`fat\`,\`calorie\`,\`source_name\`,\`source_ref\`,\`data_version\`,\`enabled\`,\`sort_order\`) VALUES\n` +
  values.join(',\n') +
  `\nON DUPLICATE KEY UPDATE\n` +
  `  \`aliases\`=VALUES(\`aliases\`),\`category\`=VALUES(\`category\`),\`serving_state\`=VALUES(\`serving_state\`),\n` +
  `  \`protein\`=VALUES(\`protein\`),\`carb\`=VALUES(\`carb\`),\`fat\`=VALUES(\`fat\`),\`calorie\`=VALUES(\`calorie\`),\n` +
  `  \`source_name\`=VALUES(\`source_name\`),\`source_ref\`=VALUES(\`source_ref\`),\`data_version\`=VALUES(\`data_version\`),\n` +
  `  \`enabled\`=VALUES(\`enabled\`),\`sort_order\`=VALUES(\`sort_order\`);\n` + exerciseSection;

fs.writeFileSync(outputPath, sql);
console.log(`已生成 ${mappings.length} 条 USDA 食材：${outputPath}`);
