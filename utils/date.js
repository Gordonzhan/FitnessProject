/** 将月、日等数字补齐为两位字符串。 */
function pad(value) {
  return String(value).padStart(2, '0');
}

/** 按设备本地时区输出 yyyy-MM-dd，避免 UTC 转换导致日期偏移。 */
function toLocalDateString(date = new Date()) {
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

/** 将 yyyy-MM-dd 解析为本地正午 Date，降低夏令时边界造成的偏移风险。 */
function parseLocalDate(value) {
  const parts = String(value || '').split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
  return new Date(parts[0], parts[1] - 1, parts[2], 12, 0, 0, 0);
}

/** 在本地日期字符串上增加或减少指定天数。 */
function addLocalDays(value, days) {
  const date = parseLocalDate(value);
  if (!date) return '';
  date.setDate(date.getDate() + days);
  return toLocalDateString(date);
}

/** 比较两个规范的 yyyy-MM-dd 字符串，返回值语义与 localeCompare 相同。 */
function compareLocalDates(left, right) {
  return String(left).localeCompare(String(right));
}

module.exports = { toLocalDateString, parseLocalDate, addLocalDays, compareLocalDates };
