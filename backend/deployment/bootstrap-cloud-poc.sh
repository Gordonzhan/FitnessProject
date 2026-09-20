#!/usr/bin/env bash
set -euo pipefail

# Bootstrap a brand-new CloudBase MySQL database for the Cloud Hosting PoC.
# The script deliberately refuses to touch a schema that already contains tables.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DB_HOST="${FITNESS_DB_HOST:-}"
DB_PORT="${FITNESS_DB_PORT:-3306}"
DB_USERNAME="${FITNESS_DB_USERNAME:-}"
DB_PASSWORD="${FITNESS_DB_PASSWORD:-}"
DB_NAME="fitness_diary"

if [[ -z "${DB_HOST}" || -z "${DB_USERNAME}" ]]; then
  echo "请先设置 FITNESS_DB_HOST 和 FITNESS_DB_USERNAME。" >&2
  exit 2
fi

if [[ -z "${DB_PASSWORD}" ]]; then
  read -r -s -p "MySQL 密码: " DB_PASSWORD
  echo
fi

for command in mysql mktemp chmod rm; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少命令：${command}" >&2
    exit 2
  fi
done

MYSQL_CONFIG="$(mktemp "${TMPDIR:-/tmp}/fitness-mysql.XXXXXX")"
cleanup() {
  rm -f "${MYSQL_CONFIG}"
}
trap cleanup EXIT INT TERM
chmod 600 "${MYSQL_CONFIG}"
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword=%s\ndefault-character-set=utf8mb4\n' \
  "${DB_HOST}" "${DB_PORT}" "${DB_USERNAME}" "${DB_PASSWORD}" >"${MYSQL_CONFIG}"
unset DB_PASSWORD

mysql_base=(mysql "--defaults-extra-file=${MYSQL_CONFIG}" --batch --skip-column-names)

existing_tables="$(${mysql_base[@]} -e \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}'")"
if [[ "${existing_tables}" != "0" ]]; then
  echo "拒绝初始化：${DB_NAME} 已包含 ${existing_tables} 张表。此脚本只允许用于全新空库。" >&2
  exit 3
fi

echo "[1/4] 创建当前基础结构和基础数据"
"${mysql_base[@]}" <"${BACKEND_DIR}/database.sql"

# 20260908_error_monitoring.sql is intentionally omitted: database.sql already
# contains request_id and both monitoring indexes. Applying it again is invalid.
migrations=(
  20260905_expand_reference_catalog.sql
  20260905_import_official_reference_data.sql
  20260906_snapshot_and_idempotency.sql
  20260907_query_performance.sql
  20260909_system_recipe_templates.sql
  20260910_expand_system_recipe_templates.sql
  20260911_chinese_system_recipe_templates.sql
  20260912_system_recipe_cover_images.sql
  20260913_remaining_system_recipe_cover_images.sql
  20260914_audit_log_classification.sql
  20260915_workout_plan_status.sql
  20260916_personal_stage_analysis.sql
  20260917_wechat_activity_steps.sql
  20260918_population_comparison.sql
  20260919_comparison_masked_display_name.sql
  20260920_image_upload_tickets.sql
)

echo "[2/4] 按发布顺序应用增量迁移"
for migration in "${migrations[@]}"; do
  echo "  - ${migration}"
  "${mysql_base[@]}" <"${BACKEND_DIR}/migrations/${migration}"
done

echo "[3/4] 清除仅供本地联调的测试账号"
"${mysql_base[@]}" "${DB_NAME}" -e "DELETE FROM users WHERE openid='test_openid'"

echo "[4/4] 校验结构与种子数据"
validation="$(${mysql_base[@]} "${DB_NAME}" -e "
SELECT CONCAT(
  (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='fitness_diary'), '|',
  (SELECT COUNT(*) FROM food_database), '|',
  (SELECT COUNT(*) FROM exercise_database), '|',
  (SELECT COUNT(*) FROM system_recipe_templates), '|',
  (SELECT COUNT(*) FROM system_recipe_ingredients), '|',
  (SELECT COUNT(*) FROM users WHERE openid='test_openid')
)")"

IFS='|' read -r table_count food_count exercise_count template_count ingredient_count test_user_count <<<"${validation}"
if (( table_count < 14 || food_count < 70 || exercise_count < 50 || template_count < 40 || ingredient_count < 100 )) \
  || [[ "${test_user_count}" != "0" ]]; then
  echo "初始化校验失败：tables=${table_count}, foods=${food_count}, exercises=${exercise_count}, templates=${template_count}, ingredients=${ingredient_count}, testUsers=${test_user_count}" >&2
  exit 4
fi

echo "初始化成功：tables=${table_count}, foods=${food_count}, exercises=${exercise_count}, templates=${template_count}, ingredients=${ingredient_count}, testUsers=${test_user_count}"
