# 参考数据库离线导入

运行时只查询项目自己的 MySQL，不会让小程序直接访问第三方接口。

## 食材

- 数据源：USDA FoodData Central Foundation Foods。
- 当前版本：`foundation-2026-04`。
- 原始营养数据保持 FDC 编号；中文名称、别名和分类维护在
  `fooddata-central-foundation-map.json`。
- 下载官方 Foundation Foods JSON 后执行：

```bash
node tools/generate-reference-food-sql.js \
  /path/to/FoodData_Central_foundation_food_json_2026-04-30.json \
  backend/migrations/20260905_import_official_reference_data.sql
```

生成器会更新 SQL 的食材段，并保留同一文件内的 Compendium 训练段。导入 SQL 使用
`ON DUPLICATE KEY UPDATE`，可以重复执行。

## 训练

- 数据源：2024 Adult Compendium of Physical Activities。
- `source_ref` 中 `CPA:` 后的五位数字是官方 Specific Activity Code。
- MET 与活动编码来自 Compendium；中文名称、别名、分类、肌群和器械标签由项目维护。

第三方数据更新时，应先在测试库生成和抽查，再执行生产库迁移。MET 仅用于活动消耗估算，
不应作为医疗建议。
