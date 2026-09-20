-- 只在 H2 内存库运行，绝不导入本地 fitness_diary。
CREATE TABLE workouts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workout_id VARCHAR(50) NOT NULL,
    date DATE NOT NULL,
    total_calories DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    estimated_calories DECIMAL(10,2) NOT NULL DEFAULT 0,
    actual_calories DECIMAL(10,2),
    created_at TIMESTAMP, updated_at TIMESTAMP,
    UNIQUE (user_id, workout_id), UNIQUE (user_id, date)
);
CREATE TABLE exercises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workout_id BIGINT NOT NULL REFERENCES workouts(id),
    name VARCHAR(50) NOT NULL,
    weight DECIMAL(10,2), sets INT NOT NULL, reps INT NOT NULL,
    calories DECIMAL(10,2) NOT NULL
);
-- 故障注入：阻止删除父记录，验证此前已删除的明细能够恢复。
CREATE TABLE workout_delete_guard (workout_id BIGINT REFERENCES workouts(id));
CREATE TABLE recipes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, recipe_id VARCHAR(50) NOT NULL,
    meal_type VARCHAR(20) NOT NULL, steps TEXT,
    protein DECIMAL(10,2) NOT NULL, carb DECIMAL(10,2) NOT NULL,
    fat DECIMAL(10,2) NOT NULL, calorie DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP, updated_at TIMESTAMP,
    UNIQUE (user_id, recipe_id)
);
CREATE TABLE recipe_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id),
    image_url VARCHAR(255) NOT NULL, sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE ingredients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id),
    food_name VARCHAR(50) NOT NULL, weight DECIMAL(10,2) NOT NULL,
    protein DECIMAL(10,2) NOT NULL, carb DECIMAL(10,2) NOT NULL,
    fat DECIMAL(10,2) NOT NULL, calorie DECIMAL(10,2) NOT NULL
);
CREATE TABLE recipe_delete_guard (recipe_id BIGINT REFERENCES recipes(id));
CREATE TABLE system_recipe_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, template_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL, aliases VARCHAR(255), category VARCHAR(30) NOT NULL,
    cuisine_type VARCHAR(20) NOT NULL DEFAULT '其他',
    cover_image_url VARCHAR(500),
    goal_tags VARCHAR(100), serving_description VARCHAR(100), steps TEXT,
    protein DECIMAL(10,2) NOT NULL, carb DECIMAL(10,2) NOT NULL,
    fat DECIMAL(10,2) NOT NULL, calorie DECIMAL(10,2) NOT NULL,
    allergen_info VARCHAR(255), source_name VARCHAR(50), source_ref VARCHAR(255),
    data_version VARCHAR(50), enabled BOOLEAN NOT NULL DEFAULT TRUE, sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE system_recipe_ingredients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, template_id BIGINT NOT NULL REFERENCES system_recipe_templates(id),
    food_name VARCHAR(50) NOT NULL, weight DECIMAL(10,2) NOT NULL,
    protein DECIMAL(10,2) NOT NULL, carb DECIMAL(10,2) NOT NULL,
    fat DECIMAL(10,2) NOT NULL, calorie DECIMAL(10,2) NOT NULL,
    source_name VARCHAR(50), source_ref VARCHAR(100), data_version VARCHAR(50), sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE daily_intake (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, date DATE NOT NULL,
    recipe_id BIGINT REFERENCES recipes(id) ON DELETE SET NULL,
    request_id VARCHAR(64) NOT NULL,
    recipe_business_id VARCHAR(50) NOT NULL,
    meal_type_snapshot VARCHAR(20) NOT NULL,
    protein_snapshot DECIMAL(10,2) NOT NULL,
    carb_snapshot DECIMAL(10,2) NOT NULL,
    fat_snapshot DECIMAL(10,2) NOT NULL,
    calorie_snapshot DECIMAL(10,2) NOT NULL,
    UNIQUE (user_id, request_id)
);
CREATE TABLE operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, request_id VARCHAR(64), ip VARCHAR(50),
    event_type VARCHAR(50), target_ref VARCHAR(100), result VARCHAR(10),
    interface_name VARCHAR(255), method VARCHAR(10), request_params TEXT,
    status_code INT, error_message TEXT, execution_time BIGINT, operation_time TIMESTAMP
);
