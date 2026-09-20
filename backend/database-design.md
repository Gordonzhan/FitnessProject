# 健身饮食日记小程序 - 数据库设计

## 1. 数据库表结构

### 1.1 用户表 (users)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 用户ID |
| `openid` | `VARCHAR(50)` | `UNIQUE NOT NULL` | 微信OpenID |
| `gender` | `VARCHAR(10)` | `NOT NULL` | 性别 (male/female) |
| `age` | `INT` | `NOT NULL` | 年龄 |
| `height` | `DECIMAL(5,2)` | `NOT NULL` | 身高 (cm) |
| `weight` | `DECIMAL(5,2)` | `NOT NULL` | 体重 (kg) |
| `goal` | `VARCHAR(20)` | `NOT NULL` | 健身目标 (lose_weight/gain_muscle/maintain) |
| `activity_level` | `DECIMAL(3,3)` | `NOT NULL` | 活动水平系数 |
| `created_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

### 1.2 菜谱表 (recipes)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 菜谱ID |
| `user_id` | `BIGINT` | `NOT NULL REFERENCES users(id)` | 用户ID |
| `recipe_id` | `VARCHAR(50)` | `NOT NULL` | 对外使用的菜谱业务ID |
| `meal_type` | `VARCHAR(20)` | `NOT NULL` | 餐型 (早餐/午餐/晚餐/加餐) |
| `cover_image` | `VARCHAR(255)` | | 封面图片 |
| `steps` | `TEXT` | | 制作步骤 |
| `protein` | `DECIMAL(10,2)` | `NOT NULL` | 蛋白质 (g) |
| `carb` | `DECIMAL(10,2)` | `NOT NULL` | 碳水 (g) |
| `fat` | `DECIMAL(10,2)` | `NOT NULL` | 脂肪 (g) |
| `calorie` | `DECIMAL(10,2)` | `NOT NULL` | 热量 (kcal) |
| `created_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

### 1.3 菜谱图片表 (recipe_images)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 图片ID |
| `recipe_id` | `BIGINT` | `NOT NULL REFERENCES recipes(id)` | 菜谱数据库主键外键 |
| `image_url` | `VARCHAR(255)` | `NOT NULL` | 图片URL |
| `sort_order` | `INT` | `NOT NULL DEFAULT 0` | 排序顺序 |

### 1.4 食材表 (ingredients)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 食材ID |
| `recipe_id` | `BIGINT` | `NOT NULL REFERENCES recipes(id)` | 菜谱数据库主键外键 |
| `food_name` | `VARCHAR(50)` | `NOT NULL` | 食物名称 |
| `weight` | `DECIMAL(10,2)` | `NOT NULL` | 重量 (g) |
| `protein` | `DECIMAL(10,2)` | `NOT NULL` | 蛋白质 (g) |
| `carb` | `DECIMAL(10,2)` | `NOT NULL` | 碳水 (g) |
| `fat` | `DECIMAL(10,2)` | `NOT NULL` | 脂肪 (g) |
| `calorie` | `DECIMAL(10,2)` | `NOT NULL` | 热量 (kcal) |

### 1.5 健身记录表 (workouts)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 记录ID |
| `user_id` | `BIGINT` | `NOT NULL REFERENCES users(id)` | 用户ID |
| `workout_id` | `VARCHAR(50)` | `NOT NULL` | 对外使用的训练业务ID |
| `date` | `DATE` | `NOT NULL` | 训练日期 |
| `total_calories` | `DECIMAL(10,2)` | `NOT NULL` | 兼容字段：计划为预计值，完成后为实际值 |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'COMPLETED'` | PLANNED / COMPLETED / CANCELLED |
| `estimated_calories` | `DECIMAL(10,2)` | `NOT NULL DEFAULT 0` | 动作和时长计算的预计消耗 |
| `actual_calories` | `DECIMAL(10,2)` | `NULL` | 确认完成后计入能量分析的实际消耗 |
| `created_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

### 1.6 训练动作表 (exercises)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 动作ID |
| `workout_id` | `BIGINT` | `NOT NULL REFERENCES workouts(id)` | 训练记录数据库主键外键 |
| `name` | `VARCHAR(50)` | `NOT NULL` | 动作名称 |
| `weight` | `DECIMAL(10,2)` | | 重量 (kg) |
| `sets` | `INT` | `NOT NULL` | 组数 |
| `reps` | `INT` | `NOT NULL` | 次数 |
| `calories` | `DECIMAL(10,2)` | `NOT NULL` | 消耗热量 |

### 1.7 今日饮食表 (daily_intake)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 记录ID |
| `user_id` | `BIGINT` | `NOT NULL REFERENCES users(id)` | 用户ID |
| `date` | `DATE` | `NOT NULL` | 日期 |
| `recipe_id` | `BIGINT` | `NOT NULL REFERENCES recipes(id)` | 菜谱数据库主键外键 |
| `created_at` | `TIMESTAMP` | `DEFAULT CURRENT_TIMESTAMP` | 创建时间 |

### 1.8 食物数据库表 (food_database)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 食物ID |
| `food_name` | `VARCHAR(50)` | `NOT NULL UNIQUE` | 食物名称 |
| `protein` | `DECIMAL(10,2)` | `NOT NULL` | 蛋白质 (g/100g) |
| `carb` | `DECIMAL(10,2)` | `NOT NULL` | 碳水 (g/100g) |
| `fat` | `DECIMAL(10,2)` | `NOT NULL` | 脂肪 (g/100g) |
| `calorie` | `DECIMAL(10,2)` | `NOT NULL` | 热量 (kcal/100g) |

### 1.9 训练动作数据库表 (exercise_database)
| 字段名 | 数据类型 | 约束 | 描述 |
|-------|---------|------|------|
| `id` | `BIGINT` | `PRIMARY KEY AUTO_INCREMENT` | 动作ID |
| `name` | `VARCHAR(50)` | `NOT NULL UNIQUE` | 动作名称 |
| `met` | `DECIMAL(3,1)` | `NOT NULL` | 代谢当量 |

## 2. 数据模型关系

1. **用户与菜谱**：一对多关系，一个用户可以创建多个菜谱
2. **用户与健身记录**：一对多关系，一个用户可以有多个健身记录
3. **用户与今日饮食**：一对多关系，一个用户可以有多个今日饮食记录
4. **菜谱与图片**：一对多关系，一个菜谱可以有多个图片
5. **菜谱与食材**：一对多关系，一个菜谱可以有多个食材
6. **健身记录与训练动作**：一对多关系，一个健身记录可以有多个训练动作
7. **今日饮食与菜谱**：多对多关系，通过 daily_intake 表关联

## 3. 索引设计

1. **users表**：
   - `openid` 字段建立唯一索引

2. **recipes表**：
   - `user_id` 字段建立普通索引
   - `recipe_id` 字段建立普通索引

3. **workouts表**：
   - `user_id` 字段建立普通索引
   - `date` 字段建立普通索引
   - `(user_id, status, date)` 建立复合索引，按用户和完成状态统计实际消耗

4. **daily_intake表**：
   - `user_id` 字段建立普通索引
   - `date` 字段建立普通索引
   - `recipe_id` 字段建立普通索引

5. **food_database表**：
   - `food_name` 字段建立唯一索引

6. **exercise_database表**：
   - `name` 字段建立唯一索引

## 4. 数据初始化

### 4.1 食物数据库初始化

```sql
INSERT INTO food_database (food_name, protein, carb, fat, calorie) VALUES
('鸡胸肉', 25.0, 0.0, 5.0, 165.0),
('米饭', 2.6, 25.6, 0.3, 116.0),
('鸡蛋', 13.0, 1.1, 8.6, 155.0),
('牛奶', 3.4, 5.0, 3.2, 66.0),
('苹果', 0.3, 13.8, 0.2, 52.0),
('牛肉', 26.0, 0.0, 15.0, 250.0),
('猪肉', 20.0, 0.0, 30.0, 350.0),
('鱼肉', 22.0, 0.0, 8.0, 180.0),
('虾', 24.0, 0.0, 2.0, 100.0),
('面包', 9.0, 50.0, 3.0, 265.0),
('面条', 8.0, 25.0, 1.0, 140.0),
('土豆', 2.0, 17.0, 0.1, 77.0),
('西红柿', 1.0, 4.0, 0.2, 18.0),
('黄瓜', 0.8, 3.0, 0.2, 15.0),
('胡萝卜', 1.0, 10.0, 0.2, 41.0),
('西兰花', 2.8, 7.0, 0.4, 34.0),
('生菜', 1.4, 3.0, 0.2, 15.0),
('豆腐', 8.0, 2.0, 4.0, 76.0),
('香蕉', 1.1, 23.0, 0.3, 89.0),
('橙子', 0.9, 12.0, 0.2, 47.0);
```

### 4.2 训练动作数据库初始化

```sql
INSERT INTO exercise_database (name, met) VALUES
('卧推', 5.0),
('深蹲', 5.0),
('硬拉', 6.0),
('引体向上', 8.0),
('跑步', 9.8);
```
