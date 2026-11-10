from __future__ import annotations

import json
import logging
from pathlib import Path

from app.core.logging import configure_logging, logger


def test_configure_logging_persists_structured_diagnostics(tmp_path: Path) -> None:
    log_path = tmp_path / "fitness-agent.log"
    configure_logging("INFO", str(log_path))

    logger.warning(
        "workout_draft_validation_failed",
        extra={
            "errorCode": "rpe_validation_failed",
            "validationDetails": [
                {"field": "exercises.rpe", "exerciseCode": "EX_ONE", "rpe": 9}
            ],
        },
    )
    for handler in logging.getLogger().handlers:
        handler.flush()

    payload = json.loads(log_path.read_text(encoding="utf-8").strip())
    assert payload["message"] == "workout_draft_validation_failed"
    assert payload["timestamp"].endswith("+00:00")
    assert payload["localTime"]
    assert payload["localTime"][-6] in {"+", "-"}
    assert payload["errorCode"] == "rpe_validation_failed"
    assert payload["validationDetails"][0]["field"] == "exercises.rpe"


def test_rag_log_keeps_stage_metrics_without_query_or_content(tmp_path: Path) -> None:
    log_path = tmp_path / "fitness-agent.log"
    configure_logging("INFO", str(log_path))

    logger.info(
        "knowledge_search_completed",
        extra={
            "topK": 5,
            "hitCount": 2,
            "documentIds": ["KB_ONE", "KB_TWO"],
            "embeddingDurationMs": 1.2,
            "keywordDurationMs": 0.3,
            "vectorDurationMs": 2.1,
            "mergeDurationMs": 0.2,
            "query": "不得出现在日志的完整问题",
            "content": "不得出现在日志的知识正文",
        },
    )
    for handler in logging.getLogger().handlers:
        handler.flush()

    raw = log_path.read_text(encoding="utf-8").strip()
    payload = json.loads(raw)
    assert payload["topK"] == 5
    assert payload["documentIds"] == ["KB_ONE", "KB_TWO"]
    assert payload["embeddingDurationMs"] == 1.2
    assert "完整问题" not in raw
    assert "知识正文" not in raw


def test_ollama_metrics_are_structured_without_model_content(tmp_path: Path) -> None:
    log_path = tmp_path / "fitness-agent.log"
    configure_logging("INFO", str(log_path))

    logger.info(
        "ollama_agent_metrics",
        extra={
            "promptEvalCount": 1200,
            "promptEvalDurationMs": 1500.0,
            "evalCount": 80,
            "evalDurationMs": 900.0,
            "modelInputCharacters": 6400,
            "modelMessageCount": 5,
            "toolSchemaCount": 0,
            "content": "不得记录模型输入正文",
        },
    )
    for handler in logging.getLogger().handlers:
        handler.flush()

    raw = log_path.read_text(encoding="utf-8").strip()
    payload = json.loads(raw)
    assert payload["promptEvalCount"] == 1200
    assert payload["modelInputCharacters"] == 6400
    assert payload["toolSchemaCount"] == 0
    assert "模型输入正文" not in raw
