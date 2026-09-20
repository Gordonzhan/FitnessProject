#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_DIR="$(cd "${BACKEND_DIR}/.." && pwd)"
OUTPUT_DIR="${PROJECT_DIR}/deploy"
OUTPUT_FILE="${OUTPUT_DIR}/fitness-cloud-poc-backend.zip"

if ! command -v zip >/dev/null 2>&1; then
  echo "缺少 zip 命令。" >&2
  exit 2
fi

mkdir -p "${OUTPUT_DIR}"
rm -f "${OUTPUT_FILE}"

# 上传源码小包；云端通过国内 Maven 镜像构建，避免直接访问 Maven Central。
(
  cd "${BACKEND_DIR}"
  zip -q -r "${OUTPUT_FILE}" Dockerfile .dockerignore pom.xml maven-settings-cloud.xml src/main
)

if unzip -l "${OUTPUT_FILE}" | grep -Eq '(^|/)(\.env|target|migrations|dev-data)(/|$)'; then
  echo "部署包包含不允许上传的本地文件，已中止。" >&2
  rm -f "${OUTPUT_FILE}"
  exit 3
fi

echo "已生成：${OUTPUT_FILE}"
unzip -l "${OUTPUT_FILE}" | tail -n 1
