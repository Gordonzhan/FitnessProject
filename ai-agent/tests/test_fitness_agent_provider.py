from __future__ import annotations

import json
import logging

import httpx
import pytest

from app.core.config import Settings
from app.providers.agent import OllamaFitnessAgentProvider


@pytest.mark.asyncio
async def test_ollama_agent_accepts_fenced_json_and_normalizes_workout_request_object(
    caplog: pytest.LogCaptureFixture,
) -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured.update(json.loads(request.content))
        return httpx.Response(
            200,
            json={
                "message": {
                    "content": """```json
                    {"reply":"已生成草稿","action":"workout_draft",
                     "workoutRequest":{"focus":"肩部","durationMinutes":45}}
                    ```"""
                },
                "load_duration": 3000000,
                "prompt_eval_count": 1200,
                "prompt_eval_duration": 1500000000,
                "eval_count": 80,
                "eval_duration": 900000000,
            },
        )

    provider = OllamaFitnessAgentProvider(
        Settings(
            environment="test",
            provider="ollama",
            chat_provider="ollama",
            ollama_base_url="http://ollama.test",
            ollama_agent_model="qwen3:8b",
        ),
        httpx.MockTransport(handler),
    )

    with caplog.at_level(logging.INFO, logger="fitness.agent"):
        result = await provider.next_turn(
            [{"role": "user", "content": "把这个计划纳入明天的训练草稿"}], []
        )

    assert result.action == "WORKOUT_DRAFT"
    assert result.workout_request == '{"focus":"肩部","durationMinutes":45}'
    assert isinstance(captured["format"], dict)
    assert captured["keep_alive"] == "30m"
    metric = next(record for record in caplog.records if record.message == "ollama_agent_metrics")
    assert metric.__dict__["promptEvalCount"] == 1200
    assert metric.__dict__["promptEvalDurationMs"] == 1500.0
    assert metric.__dict__["evalCount"] == 80
    assert metric.__dict__["evalDurationMs"] == 900.0
    assert metric.__dict__["modelMessageCount"] == 1


@pytest.mark.asyncio
async def test_ollama_agent_uses_latest_user_message_when_draft_request_is_missing() -> None:
    def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "message": {
                    "content": json.dumps(
                        {"reply": "可以", "action": "WORKOUT_DRAFT", "workoutRequest": None},
                        ensure_ascii=False,
                    )
                }
            },
        )

    provider = OllamaFitnessAgentProvider(
        Settings(environment="test", provider="ollama", ollama_base_url="http://ollama.test"),
        httpx.MockTransport(handler),
    )

    result = await provider.next_turn(
        [
            {"role": "user", "content": "先给我一个练肩计划"},
            {"role": "assistant", "content": "这里是计划"},
            {"role": "user", "content": "把这个计划纳入明天的训练草稿"},
        ],
        [],
    )

    assert result.workout_request == "把这个计划纳入明天的训练草稿"
