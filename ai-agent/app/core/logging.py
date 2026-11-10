from __future__ import annotations

import json
import logging
from datetime import UTC, datetime
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any

from app.core.middleware import current_request_id


class JsonFormatter(logging.Formatter):
    """将日志转换为便于检索和接入日志平台的单行 JSON。"""

    def format(self, record: logging.LogRecord) -> str:
        """写入通用字段，并透传业务日志通过 extra 提供的可观测字段。"""

        now = datetime.now(UTC)
        payload: dict[str, Any] = {
            "timestamp": now.isoformat(),
            "localTime": now.astimezone().isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "requestId": current_request_id.get(),
        }
        for key in (
            "provider", "phase", "toolCount", "candidateCount", "method", "path", "statusCode",
            "durationMs", "errorType", "errorCode", "validationDetails", "topK", "hitCount",
            "documentIds", "embeddingDurationMs", "keywordDurationMs", "vectorDurationMs",
            "mergeDurationMs", "collection", "chunkCount", "embeddingModel",
            "loadDurationMs", "promptEvalDurationMs", "promptEvalCount", "evalDurationMs",
            "evalCount", "modelInputCharacters", "modelMessageCount", "toolSchemaCount",
        ):
            value = getattr(record, key, None)
            if value is not None:
                payload[key] = value
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: str, log_file: str | None = None) -> None:
    """统一配置控制台与可选滚动文件日志。"""

    formatter = JsonFormatter()
    handlers: list[logging.Handler] = [logging.StreamHandler()]
    if log_file:
        path = Path(log_file).expanduser()
        path.parent.mkdir(parents=True, exist_ok=True)
        handlers.append(
            RotatingFileHandler(
                path,
                maxBytes=10 * 1024 * 1024,
                backupCount=5,
                encoding="utf-8",
                delay=True,
            )
        )
    for handler in handlers:
        handler.setFormatter(formatter)
    root = logging.getLogger()
    for existing in root.handlers:
        existing.close()
    root.handlers.clear()
    for handler in handlers:
        root.addHandler(handler)
    root.setLevel(level)


logger = logging.getLogger("fitness.agent")
